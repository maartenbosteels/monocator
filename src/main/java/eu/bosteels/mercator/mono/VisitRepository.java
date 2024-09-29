package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.vat.crawler.persistence.PageVisit;
import be.dnsbelgium.mercator.vat.domain.Link;
import be.dnsbelgium.mercator.vat.domain.Page;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;
import com.github.f4b6a3.ulid.Ulid;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class VisitRepository {

  // for now. we delegate to Repository. If it all works, we should move the methods to this class
  private final Repository repository;
  private final JdbcTemplate jdbcTemplate;
  private final TableCreator tableCreator;
  private final ThreadPoolTaskExecutor executor;
  private static final Logger logger = LoggerFactory.getLogger(VisitRepository.class);

  private final ReadWriteLock readWriteLock;
  private final AtomicInteger transactionCount = new AtomicInteger(0);

  private final static Duration WARN_AFTER = Duration.ofSeconds(5);

  private String databaseName;
  private File databaseFile;

  @Setter
  @Value("${vat.crawler.persist.page.visits:false}")
  private boolean persistPageVisits = false;

  @Setter
  @Value("${vat.crawler.persist.first.page.visit:false}")
  private boolean persistFirstPageVisit = false;

  @Setter
  @Value("${vat.crawler.persist.body.text:false}")
  private boolean persistBodyText = false;

  @Value("${visits.export.directory}")
  private File exportDirectory;

  @Value("${visits.database.directory}")
  private File databaseDirectory;

  @Value("${visits.max.transactions.per_db:10000}")
  int maxTransactionsPerDatabase;

  @Value("${visits.database.deleteAfterExport:true}")
  boolean deleteDatabaseAfterExport;

  @Autowired
  public VisitRepository(DuckDataSource dataSource,
                         TableCreator tableCreator,
                         @Qualifier("insertExecutor") ThreadPoolTaskExecutor executor
  ) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.repository = new Repository(dataSource);
    this.tableCreator = tableCreator;
    this.readWriteLock = new ReentrantReadWriteLock();
    this.executor = executor;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Transactional
  public void start() {
    databaseDirectory.mkdirs();
    exportDirectory.mkdirs();
    // TODO: we should check if an unfinished database still exists from a previous run
    logger.info("creating a new database");
    newDatabase();
    // we need a database to 'use' before detaching an exported visit database.
    executeStatement("ATTACH ':memory:' AS memory_db");
  }

  public ReadWriteLock readWriteLock() {
    return readWriteLock;
  }

  @Transactional
  public void afterCommit() {
    int count = transactionCount.incrementAndGet();
    logger.debug("transactionCount: {}", count);
    if (count == maxTransactionsPerDatabase) {
      logger.info("current database already had {} transactions => exporting and then starting new db", count);
      exportAndStartNewDatabase();
    }
  }

  public Map<String, Object> databaseSize() {
    Map<String, Object> map = jdbcTemplate.queryForMap(
            "select * from pragma_database_size() where database_name = ?", databaseName);
    logger.debug("map = {}", map);
    return map;
  }


  private void exportAndStartNewDatabase() {
    var start = Instant.now();
    readWriteLock.writeLock().lock();
    var end = Instant.now();
    logger.info("We now have the writelock after waiting {}", Duration.between(start, end));
    try {
      exportDatabase();
      newDatabase();
      logTables();
      logDatabases();
      logger.info("exportAndStartNewDatabase: searchPath = [{}]", searchPath());
    } finally {
      readWriteLock.writeLock().unlock();
      logger.info("We now have released the writelock after {}", Duration.between(start, Instant.now()));
      logger.info("exportAndStartNewDatabase now: {}", start);
    }
  }

  private void newDatabase() {
    readWriteLock.writeLock().lock();
    try {
      this.databaseName = "visits_db_" + Ulid.fast();
      this.databaseFile = new File(databaseDirectory, databaseName + ".db");
      attachAndUse();
      tableCreator.createVisitTables();
      transactionCount.set(0);
      // log name of database in scheduler DB ?
      // logTables();
    } finally {
      // this call will not always release the lock, it could also just decrement the held count
      readWriteLock.writeLock().unlock();
    }
  }

  public void logTables() {
    var x = "show all tables";
    var tables = jdbcTemplate.queryForList("select database, schema, name from (" + x + ") ");
    for (var table : tables) {
      logger.debug("{}.{}.{}", table.get("database"), table.get("schema"), table.get("name"));
    }
  }

  public String searchPath() {
    var query = "SELECT current_setting('search_path')";
    return jdbcTemplate.queryForObject(query, String.class);
  }

  public Long transactionId() {
    var query = "SELECT txid_current()";
    return jdbcTemplate.queryForObject(query, Long.class);
  }


  private void exportDatabase() {
      String destinationDir = exportDirectory.getAbsolutePath() + File.separator + databaseName + File.separator;
      logger.info("destinationDir = {}", destinationDir);
      var transactionId = transactionId();
      logger.warn("before export: transactionId = {}", transactionId);
      executeStatement("use " + databaseName);
      String export = """
                    export database '%s'
                    (
                        FORMAT PARQUET,
                        COMPRESSION ZSTD,
                        ROW_GROUP_SIZE 100_000
                    )
                    """.formatted(destinationDir);
      var duration = executeStatement(export);
      logger.warn("Exporting to {} took {}", destinationDir, duration);
      logger.warn("after export: transactionId = {}", transactionId);
      executeStatement("use memory_db");
      executeStatement("DETACH " + databaseName);
      if (deleteDatabaseAfterExport) {
        deleteDatabaseFile();
      }
  }

  private void deleteDatabaseFile() {
    try {
      FileUtils.delete(databaseFile);
      logger.info("deleted {}", databaseFile);
    } catch (IOException e) {
      logger.atError()
              .setMessage("Could not delete database file {}")
              .addArgument(databaseFile)
              .setCause(e)
              .log();
    }
  }

  private Duration executeStatement(String sql) {
    var started = Instant.now();
    jdbcTemplate.execute(sql);
    var finished = Instant.now();
    var duration = Duration.between(started, finished);
    if (duration.compareTo(WARN_AFTER) > 0) {
      logger.warn("Statement took {} SQL: {}", duration, sql);
    }
    logger.debug("Done executing sql = {} took {}", sql, duration);
    //repository.saveOperation(started, sql, duration);
    return duration;
  }

  private void logDatabases() {
    var databases = jdbcTemplate.queryForList("show databases", String.class);
    for (var database : databases) {
      logger.debug("we have this database attached: '{}' ", database);
    }

  }
  
  private void attachAndUse() {
    var attach = String.format("ATTACH if not exists '%s' AS %s", databaseFile.getAbsolutePath(), databaseName);
    logger.info("attach = {}", attach);
    executeStatement(attach);
    executeStatement("use " + databaseName);
  }

  public void saveAsync(VisitResult visitResult) {
    executor.execute(() -> save(visitResult));
  }


  @Transactional
  public void save(VisitResult visitResult) {
    var start = Instant.now();
    var dbName = databaseName;
    try {
      logDatabases();
      logger.info("save:: now: {}", start);

      attachAndUse();

      for (HtmlFeatures htmlFeatures : visitResult.featuresList()) {
        repository.save(htmlFeatures);
      }
      repository.save(visitResult.dnsCrawlResult());

      repository.save(visitResult.vatCrawlResult());
      savePageVisits(visitResult.visitRequest(), visitResult.siteVisit());

      var duration = Duration.between(start, Instant.now());
      logger.debug("Done saving VisitResult for {}, took {}", visitResult.visitRequest(), duration);
    } catch (Exception e) {
      logger.info("save on {} started at {} failed", dbName, start);
      throw e;
    }
  }


  private void savePageVisits(VisitRequest visitRequest, SiteVisit siteVisit) {
    logger.debug("Persisting the {} page visits for {}", siteVisit.getNumberOfVisitedPages(), siteVisit.getBaseURL());

    for (Map.Entry<Link, Page> linkPageEntry : siteVisit.getVisitedPages().entrySet()) {
      Page page = linkPageEntry.getValue();

      boolean isLandingPage = page.getUrl().equals(siteVisit.getBaseURL());
      boolean saveLandingPage = (isLandingPage & persistFirstPageVisit);

      if (persistPageVisits || page.isVatFound() || saveLandingPage) {
        boolean includeBodyText = persistBodyText || page.isVatFound() || saveLandingPage;
        PageVisit pageVisit = page.asPageVisit(visitRequest, includeBodyText);
        pageVisit.setLinkText(linkPageEntry.getKey().getText());
        repository.save(pageVisit);

      }
    }
  }

}

package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import com.github.f4b6a3.ulid.Ulid;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// @Component
public class VisitDatabase {

    private DuckDataSource dataSource;
    private JdbcTransactionManager transactionManager;
    private JdbcTemplate jdbcTemplate;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final AtomicInteger transactionCount = new AtomicInteger(0);
    private static final Logger logger = LoggerFactory.getLogger(VisitDatabase.class);

    private final File databaseDirectory;
    private final File exportDirectory;
    private final int maxTransactionsPerDatabase;
    private final boolean deleteDatabaseAfterExport;

    private final static String JDBC_URL_PREFIX = "jdbc:duckdb://";
    private final static String JDBC_URL_SUFFIX = ".duckdb";
    private final static Duration WARN_AFTER = Duration.ofSeconds(5);

    private String databaseName;
    private File databaseFile;

    @PostConstruct
    public void init() throws IOException {
        logger.debug("databaseDirectory = {}", databaseDirectory);
        FileUtils.forceMkdir(databaseDirectory);
        logger.debug("exportDirectory = {}", exportDirectory);
        FileUtils.forceMkdir(exportDirectory);
        // TODO: re-open database from previous run?
        // store location of current database in the scheduler DB ?
        newDatabase();
    }

    @PreDestroy
    public void close() {
        if (dataSource != null) {
            logger.info("Closing the Visit Database: {}", databaseFile);
            dataSource.close();
        }
    }

    public interface Callback<T> {
        T doInTransaction(JdbcTemplate jdbcTemplate);
    }

    public VisitDatabase(
            @Value("${visits.database.directory:${user.home}/monocator/db/}")    File databaseDirectory,
            @Value("${visits.export.directory:${user.home}/monocator/exports/}") File exportDirectory,
            @Value("${visits.max.transactions.per_db:10000}") int maxTransactionsPerDatabase,
            @Value("${visits.database.deleteAfterExport:true}") boolean deleteDatabaseAfterExport
    ) {
        this.databaseDirectory = databaseDirectory;
        this.exportDirectory = exportDirectory;
        this.maxTransactionsPerDatabase = maxTransactionsPerDatabase;
        this.deleteDatabaseAfterExport = deleteDatabaseAfterExport;
    }


    public <T> T doInTransaction(Callback<T> callback) {
        int count = transactionCount.getAndIncrement();
        // if we check (count >= maxTransactionsPerDatabase) then there could be two threads
        // that both think they need to start the export.
        // By checking for equality we definitely avoid that risk (I think ;-) )
        logger.info("transactionCount: {}", count);
        if (count == maxTransactionsPerDatabase) {
            logger.info("current database already had {} transactions => exporting and then starting new db", count);
            exportAndStartNewDatabase();
        }
        readWriteLock.readLock().lock();
        try {
            var tx = transactionManager.getTransaction(TransactionDefinition.withDefaults());
            try {
                return callback.doInTransaction(jdbcTemplate);
            } catch (Exception e) {
                logger.atError().setMessage("Callable failed => rollback and throw").setCause(e).log();
                tx.setRollbackOnly();
                throw new RuntimeException(e);
            } finally {
                transactionManager.commit(tx);
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private void newDatabase() {
        readWriteLock.writeLock().lock();
        try {
            if (dataSource != null) {
                dataSource.close();
            }
            DuckDataSource dataSource = new DuckDataSource();
            this.databaseName = "visits." + Ulid.fast();
            logger.info("creating a new database: {}", databaseName);
            var url = JDBC_URL_PREFIX
                    + databaseDirectory.getAbsolutePath()
                    + File.separator + databaseName + JDBC_URL_SUFFIX;
            logger.info("URL for new visits database: {}", url);
            this.databaseFile = new File(databaseDirectory, databaseName);
            dataSource.setUrl(url);
            dataSource.init();
                /*

                Alternatief: ipv nieuwe DataSource en transactionManager aan te maken
                * toch gewoon definieren als Spring Beans
                * bij opstart:
                    * generate ulid
                    * attach 'visit_db.01J7Y34RDV0PN53HDNB7AB5VM1.duckdb' as visit_db_01J7Y34RDV0PN53HDNB7AB5VM1;
                    * save name of database in private field
                    * create visit tables (na 'use visit_db_01J7Y34RDV0PN53HDNB7AB5VM1')

               * in alle repository methods
                  * start met template.execute("visit_db_01J7Y34RDV0PN53HDNB7AB5VM1")
                  * bewaar naam van current DB in de DONE table

                * na x transactions/visits een

                    * neem write lock
                    * checkpoint huidige visit_db
                    * export huidige visit_db
                    * detach huidige visit_db
                    * generate ulid
                    * attach 'visit_db.XXXX.duckdb' as XXX;
                    * save name of database in private field
                    * create visit tables

                * cache tables: keep them in separate db like the scheduler db ?

                */

            this.dataSource = dataSource;
            this.jdbcTemplate = new JdbcTemplate(dataSource);
            this.transactionManager = new JdbcTransactionManager();
            this.transactionManager.setDataSource(dataSource);
            this.transactionCount.set(0);
            TableCreator tableCreator = new TableCreator(dataSource);
            tableCreator.createVisitTables();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private void exportAndStartNewDatabase() {
        readWriteLock.writeLock().lock();
        try {
            Objects.requireNonNull(dataSource, "Cannot export when dataSource is null");
            Objects.requireNonNull(jdbcTemplate, "Cannot export when jdbcTemplate is null");
            executeStatement("checkpoint");
            export();
            deleteDatabaseIfConfigured();
            newDatabase();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private void export() {
        String destinationDir = exportDirectory.getAbsolutePath() + File.separator + databaseName + File.separator;
        logger.info("destinationDir = {}", destinationDir);
        String export = """
                    export database '%s'
                    (
                        FORMAT PARQUET,
                        COMPRESSION ZSTD,
                        ROW_GROUP_SIZE 100_000
                    )
                    """.formatted(destinationDir);
        executeStatement(export);
    }

    private void deleteDatabaseIfConfigured() {
        logger.debug("deleteDatabaseAfterExport={}", deleteDatabaseAfterExport);
        if (deleteDatabaseAfterExport) {
            if (databaseFile.delete()) {
                logger.info("Deleted database {}", databaseFile);
            } else {
                logger.info("Could not deleted database file {}", databaseFile);
            }
        }
    }

    private void executeStatement(String sql) {
//        logger.info("Start executing sql = {}", sql);
//        var started = Instant.now();
        jdbcTemplate.execute(sql);
//        var finished = Instant.now();
//        var duration = Duration.between(started, finished);
//        if (duration.compareTo(WARN_AFTER) > 0) {
//            logger.warn("Statement took {} SQL: {}", duration, sql);
//        }
//        logger.info("Done executing sql = {} took {}", sql, duration);
        //repository.saveOperation(started, sql, duration);
    }



}

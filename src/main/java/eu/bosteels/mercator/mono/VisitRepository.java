package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.dns.persistence.Request;
import be.dnsbelgium.mercator.dns.persistence.Response;
import be.dnsbelgium.mercator.dns.persistence.ResponseGeoIp;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CrawlResultEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.FullScanEntity;
import be.dnsbelgium.mercator.vat.crawler.persistence.PageVisit;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import be.dnsbelgium.mercator.vat.domain.Link;
import be.dnsbelgium.mercator.vat.domain.Page;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;
import com.github.f4b6a3.ulid.Ulid;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.sql.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static eu.bosteels.mercator.mono.Repository.*;

@SuppressWarnings("SqlDialectInspection")
@Component
public class VisitRepository {

  private final JdbcTemplate jdbcTemplate;
  private final JdbcClient jdbcClient;
  private final TableCreator tableCreator;
  private static final Logger logger = LoggerFactory.getLogger(VisitRepository.class);

  private final static Duration WARN_AFTER = Duration.ofSeconds(5);

  private int databaseCounter = 0;
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
  @Setter @Getter
  private File exportDirectory;

  @Value("${visits.database.directory}")
  @Setter @Getter
  private File databaseDirectory;

  @Value("${visits.database.deleteAfterExport:true}")
  boolean deleteDatabaseAfterExport;

  @Autowired
  public VisitRepository(DuckDataSource dataSource,
                         TableCreator tableCreator) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.jdbcClient = JdbcClient.create(dataSource);
    this.tableCreator = tableCreator;
  }

  @Transactional
  public void init() {
    makeDatabaseDirectory();
    makeExportDirectory();
    // TODO: we should check if a database still exists from a previous run
    logger.info("creating a new database");
    newDatabase();
  }

  @SneakyThrows
  private void makeDatabaseDirectory() {
    if (databaseDirectory == null) {
      databaseDirectory = new File(System.getProperty("user.home"));
      logger.warn("databaseDirectory not set => using {}", databaseDirectory);
    }
    FileUtils.forceMkdir(databaseDirectory);
  }

  @SneakyThrows
  private void makeExportDirectory() {
    if (exportDirectory == null) {
      exportDirectory = new File(System.getProperty("user.home"));
      logger.warn("exportDirectory not set => using {}", exportDirectory);
    }
    FileUtils.forceMkdir(exportDirectory);
  }

  @Transactional
  public List<String> getTableNames() {
    attachAndUse();
    return jdbcClient
        .sql("select table_name from information_schema.tables where table_type = 'BASE TABLE' and table_catalog = ? ")
        .param(databaseName)
        .query(String.class)
        .list();
  }

  @Transactional
  public void save(VisitResult visitResult) {
    var start = Instant.now();
    var dbName = databaseName;
    try {
      logDatabases();
      logger.debug("save:: now: {}", start);

      attachAndUse();

      for (HtmlFeatures htmlFeatures : visitResult.featuresList()) {
        save(htmlFeatures);
      }
      save(visitResult.dnsCrawlResult());

      save(visitResult.vatCrawlResult());
      savePageVisits(visitResult.visitRequest(), visitResult.siteVisit());

      var duration = Duration.between(start, Instant.now());
      logger.debug("Done saving VisitResult for {}, took {}", visitResult.visitRequest(), duration);
    } catch (Exception e) {
      logger.info("save on {} started at {} failed", dbName, start);
      throw e;
    }
  }

  private void mkDir(File dir) {
    boolean ok = dir.mkdirs();
    logger.info("mkdirs {} => {}", dir, ok);
  }

  @Transactional
  public void exportDatabase(boolean attachNewDatabase) {
    exportDatabase();
    if (attachNewDatabase) {
      newDatabase();
      logTables();
      logDatabases();
    }
    logger.info("exportAndStartNewDatabase: searchPath = [{}]", searchPath());
  }

  public Map<String, Object> databaseSize() {
    Map<String, Object> map = jdbcTemplate.queryForMap(
            "select * from pragma_database_size() where database_name = ?", databaseName);
    logger.debug("map = {}", map);
    return map;
  }

  private void newDatabase() {
    this.databaseCounter++;
    // The databaseCounter will restart from zero when the process is restarted
    // Its only purpose is to make it easier to spot the most recently created database
    this.databaseName = "visits_db_" + databaseCounter + "_" + Ulid.fast();
    this.databaseFile = new File(databaseDirectory, databaseName + ".db");
    attachAndUse();
    tableCreator.createVisitTables();
    // log name of database in scheduler DB ?
    // logTables();
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
    logger.debug("before export: transactionId = {}", transactionId);
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
    logger.info("Exporting to {} took {}", destinationDir, duration);
    logger.debug("after export: transactionId = {}", transactionId);
    executeStatement("ATTACH if not exists ':memory:' AS memory ");
    executeStatement("use memory ");
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
    executeStatement(attach);
    executeStatement("use " + databaseName);
  }

  public Optional<VatCrawlResult> findVatCrawlResult(String visitId) {
    return jdbcClient
            .sql("select * from web_visit where visit_id = ?")
            .param(visitId)
            .query((rs, rowNum) -> {
              String domainName = rs.getString("domain_name");
              String startUrl = rs.getString("start_url");
              String matchingUrl = rs.getString("matching_url");
              Instant crawl_started = instant(rs.getTimestamp("crawl_started"));
              Instant crawl_finished = instant(rs.getTimestamp("crawl_finished"));
              Object[] array = (Object[]) rs.getArray("visited_urls").getArray();
              List<String> visitedUrls = Arrays.stream(array).map(o -> (String)o).toList();
              logger.info("visitedUrls = {}", visitedUrls);

              for (String visitedUrl : visitedUrls) {
                logger.info("visitedUrl = [{}]", visitedUrl);
              }

              return VatCrawlResult
                      .builder()
                      .visitId(visitId)
                      .domainName(domainName)
                      .startUrl(startUrl)
                      .crawlStarted(crawl_started)
                      .crawlFinished(crawl_finished)
                      .matchingUrl(matchingUrl)
                      .visitedUrls(visitedUrls)
                      .build();
            }).
            optional();
  }

  public List<PageVisit> findPageVisits(String visitId) {
    List<PageVisit> found = jdbcClient
            .sql("select * from web_page_visit where visit_id = ?")
            .param(visitId)
            .query((rs, rowNum) -> {
              Instant crawl_started = instant(rs.getTimestamp("crawl_started"));
              Instant crawl_finished = instant(rs.getTimestamp("crawl_finished"));
              var vat_values_array = rs.getArray("vat_values").getArray();
              List<String> vat_values = Arrays.stream((Object[]) vat_values_array).map(Object::toString).toList();
              return PageVisit
                      .builder()
                      .visitId(visitId)
                      .domainName(rs.getString("domain_name"))
                      .crawlStarted(crawl_started)
                      .crawlFinished(crawl_finished)
                      .html(rs.getString("html"))
                      .bodyText(rs.getString("body_text"))
                      .statusCode(rs.getInt("status_code"))
                      .url(rs.getString("url"))
                      .path(rs.getString("path"))
                      .vatValues(vat_values)
                      .linkText(rs.getString("link_text"))
                      .build();
            })
            .list();
    logger.debug("findPageVisits {} => found {} pages", visitId, found.size());
    return found;
  }

  private Array array(List<String> list) {
    return jdbcTemplate.execute(
            (ConnectionCallback<Array>) con ->
                    con.createArrayOf("text", list.toArray())
    );
  }

  public void save(@NotNull PageVisit pageVisit) {
    logger.debug("Saving PageVisit with url={}", pageVisit.getUrl());
    String insert = """
        insert into web_page_visit(
          visit_id,  domain_name,  crawl_started,  crawl_finished,  html,  body_text,  status_code,  url,  path,  vat_values,  link_text)
        values (
          :visit_id, :domain_name, :crawl_started, :crawl_finished, :html, :body_text, :status_code, :url, :path, :vat_values, :link_text)
        """;
    jdbcClient
            .sql(insert)
            .param("visit_id", pageVisit.getVisitId())
            .param("domain_name", pageVisit.getDomainName())
            .param("crawl_started", timestamp(pageVisit.getCrawlStarted()))
            .param("crawl_finished", timestamp(pageVisit.getCrawlFinished()))
            .param("html", pageVisit.getHtml())
            .param("body_text", pageVisit.getBodyText())
            .param("status_code", pageVisit.getStatusCode())
            .param("url", pageVisit.getUrl())
            .param("path", pageVisit.getPath())
            .param("link_text", pageVisit.getLinkText())
            .param("vat_values", array(pageVisit.getVatValues()))
            .update();
  }

  public void save(@NotNull VatCrawlResult crawlResult) {
    var insert = """
            insert into web_visit
            (
                visit_id,
                domain_name,
                start_url,
                matching_url,
                crawl_started,
                crawl_finished,
                visited_urls
            )
            values (?, ?, ?, ?, ?, ?, ?)
            """;
    Array visitedUrls = jdbcTemplate.execute(
            (ConnectionCallback<Array>) con ->
                    con.createArrayOf("text", crawlResult.getVisitedUrls().toArray())
    );
    int rowsInserted = jdbcTemplate.update(
            insert,
            crawlResult.getVisitId(),
            crawlResult.getDomainName(),
            crawlResult.getStartUrl(),
            crawlResult.getMatchingUrl(),
            timestamp(crawlResult.getCrawlStarted()),
            timestamp(crawlResult.getCrawlFinished()),
            visitedUrls
    );
    logger.debug("domain={} rowsInserted={}", crawlResult.getDomainName(), rowsInserted);
  }

  public void save(@NotNull DnsCrawlResult crawlResult) {
    logger.info("Saving crawlResult = {}", crawlResult.getStatus());
    for (var req : crawlResult.getRequests()) {
      String id = Ulid.fast().toString();
      req.setId(id);
      insertDnsRequest(req);
      for (Response response : req.getResponses()) {
        insertResponse(req, response);
        for (ResponseGeoIp geoIp : response.getResponseGeoIps()) {
          // TODO: this happens when geo ip is disabled. Can we do it cleaner?
          if (geoIp != null) {
            insertGeoIp(response, geoIp);
          }
        }
      }
    }
  }

  public void insertDnsRequest(@NotNull Request req) {
    var insert = """
            insert into dns_request(
                id,
                visit_id,
                domain_name,
                prefix,
                record_type,
                rcode,
                ok,
                problem,
                num_of_responses,
                crawl_timestamp
            )
            values
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    jdbcTemplate.update(
            insert,
            req.getId(),
            req.getVisitId(),
            req.getDomainName(),
            req.getPrefix(),
            req.getRecordType().toString(),
            req.getRcode(),
            req.isOk(),
            req.getProblem(),
            req.getNumOfResponses(),
            timestamp(req.getCrawlTimestamp())
    );
  }

  public void insertResponse(@NotNull Request request, @NotNull Response response) {
    String insert =
            """
                        insert into dns_response(id, dns_request, record_data, ttl)
                        values (?, ?, ?, ?)
                    """;
    String id = Ulid.fast().toString();
    response.setId(id);
    jdbcTemplate.update(
            insert,
            id,
            request.getId(),
            response.getRecordData(),
            response.getTtl()
    );
  }

  public void insertGeoIp(@NotNull Response response, @NotNull ResponseGeoIp responseGeoIp) {
    String insert = """
            insert into response_geo_ips
            (dns_response, asn, country, ip, asn_organisation, ip_version)
            values (?, ?, ?, ?, ?, ?)
            """;
    jdbcTemplate.update(
            insert,
            response.getId(),
            responseGeoIp.getAsn(),
            responseGeoIp.getCountry(),
            responseGeoIp.getIp(),
            responseGeoIp.getAsnOrganisation(),
            responseGeoIp.getIpVersion()
    );
  }

  public void save(@NotNull HtmlFeatures h) {
    var insert = """
            insert into html_features(
                visit_id,
                crawl_timestamp,
                domain_name,
                html_length,
                nb_imgs,
                nb_links_int,
                nb_links_ext,
                nb_links_tel,
                nb_links_email,
                nb_input_txt,
                nb_button,
                nb_meta_desc,
                nb_meta_keyw,
                nb_numerical_strings,
                nb_tags,
                nb_words,
                title,
                htmlstruct,
                body_text,
                meta_text,
                url,
                body_text_truncated,
                meta_text_truncated,
                title_truncated,
                nb_letters,
                nb_distinct_hosts_in_urls,
                external_hosts,
                nb_facebook_deep_links    ,
                nb_facebook_shallow_links ,
                nb_linkedin_deep_links    ,
                nb_linkedin_shallow_links ,
                nb_twitter_deep_links     ,
                nb_twitter_shallow_links  ,
                nb_currency_names         ,
                nb_distinct_currencies    ,
                distance_title_final_dn   ,
                longest_subsequence_title_final_dn,
                facebook_links            ,
                twitter_links             ,
                linkedin_links            ,
                youtube_links             ,
                vimeo_links               ,
                nb_youtube_deep_links     ,
                nb_youtube_shallow_links  ,
                nb_vimeo_deep_links       ,
                nb_vimeo_shallow_links    ,
                body_text_language        ,
                body_text_language_2      ,
                fraction_words_title_initial_dn,
                fraction_words_title_final_dn  ,
                nb_distinct_words_in_title,
                distance_title_initial_dn ,
                longest_subsequence_title_initial_dn
                )"""
            + values(53);
    jdbcTemplate.update(insert,
            h.visitId,
            timestamp(h.crawlTimestamp),
            h.domainName,
            h.html_length,
            h.nb_imgs,
            h.nb_links_int,
            h.nb_links_ext,
            h.nb_links_tel,
            h.nb_links_email,
            h.nb_input_txt,
            h.nb_button,
            h.nb_meta_desc,
            h.nb_meta_keyw,
            h.nb_numerical_strings,
            h.nb_tags,
            h.nb_words,
            h.title,
            h.htmlstruct,
            h.body_text,
            h.meta_text,
            h.url,
            h.body_text_truncated,
            h.meta_text_truncated,
            h.title_truncated,
            h.nb_letters,
            h.nb_distinct_hosts_in_urls,
            array(h.external_hosts),
            h.nb_facebook_deep_links,
            h.nb_facebook_shallow_links,
            h.nb_linkedin_deep_links,
            h.nb_linkedin_shallow_links,
            h.nb_twitter_deep_links,
            h.nb_twitter_shallow_links,
            h.nb_currency_names,
            h.nb_distinct_currencies,
            h.distance_title_final_dn,
            h.longest_subsequence_title_final_dn,
            array(h.facebook_links),
            array(h.twitter_links),
            array(h.linkedin_links),
            array(h.youtube_links),
            array(h.vimeo_links),
            h.nb_youtube_deep_links,
            h.nb_youtube_shallow_links,
            h.nb_vimeo_deep_links,
            h.nb_vimeo_shallow_links,
            h.body_text_language,
            h.body_text_language_2,
            h.fraction_words_title_initial_dn,
            h.fraction_words_title_final_dn,
            h.nb_distinct_words_in_title,
            h.distance_title_initial_dn,
            h.longest_subsequence_title_initial_dn
    );
  }

  public void save(CrawlResultEntity crawlResult) {
    String insert = """
                insert into tls_crawl_result(
                visit_id,
                domain_name,
                crawl_timestamp,
                full_scan,
                host_name_matches_certificate,
                host_name,
                leaf_certificate,
                certificate_expired,
                certificate_too_soon,
                chain_trusted_by_java_platform
                )
            """ + values(10);
    var leafCert = (crawlResult.getLeafCertificateEntity() == null) ? null : crawlResult.getLeafCertificateEntity().getSha256fingerprint();
    jdbcTemplate.update(
            insert,
            crawlResult.getVisitId(),
            crawlResult.getDomainName(),
            timestamp(crawlResult.getCrawlTimestamp()),
            crawlResult.getFullScanEntity().getId(),
            crawlResult.isHostNameMatchesCertificate(),
            crawlResult.getHostName(),
            leafCert,
            crawlResult.isCertificateExpired(),
            crawlResult.isCertificateTooSoon(),
            crawlResult.isChainTrustedByJavaPlatform()
    );
  }

  public void save(CertificateEntity certificate) {
    var insert = """
            insert into tls_certificate (
                sha256_fingerprint,
                version,
                public_key_schema,
                public_key_length,
                issuer,
                subject,
                signature_hash_algorithm,
                signed_by_sha256,
                serial_number_hex,
                subject_alt_names,
                not_before,
                not_after,
                insert_timestamp
            )
            values (?,?,?,?,?,?,?,?,?,?,?,?, current_timestamp)
            ON CONFLICT DO NOTHING
            """;
    //var signedBy = ( certificate.getSignedBy()== null) ? null : certificate.getSignedBy().getSha256Fingerprint();
    var signedBy = certificate.getSignedBySha256();
    jdbcTemplate.update(insert,
            certificate.getSha256fingerprint(),
            certificate.getVersion(),
            certificate.getPublicKeySchema(),
            certificate.getPublicKeyLength(),
            certificate.getIssuer(),
            certificate.getSubject(),
            certificate.getSignatureHashAlgorithm(),
            signedBy,
            certificate.getSerialNumberHex(),
            array(certificate.getSubjectAltNames()),
            timestamp(certificate.getNotBefore()),
            timestamp(certificate.getNotAfter())
    );
  }

  public void save(FullScanEntity fullScan) {
    var insert = """
            insert into tls_full_scan(
                    id,
                    crawl_timestamp,
                    ip,
                    server_name,
                    connect_ok,
                    support_tls_1_3,
                    support_tls_1_2,
                    support_tls_1_1,
                    support_tls_1_0,
                    support_ssl_3_0,
                    support_ssl_2_0,
                    selected_cipher_tls_1_3,
                    selected_cipher_tls_1_2,
                    selected_cipher_tls_1_1,
                    selected_cipher_tls_1_0,
                    selected_cipher_ssl_3_0,
                    accepted_ciphers_ssl_2_0,
                    lowest_version_supported,
                    highest_version_supported,
                    error_tls_1_3,
                    error_tls_1_2,
                    error_tls_1_1,
                    error_tls_1_0,
                    error_ssl_3_0,
                    error_ssl_2_0,
                    millis_tls_1_3,
                    millis_tls_1_2,
                    millis_tls_1_1,
                    millis_tls_1_0,
                    millis_ssl_3_0,
                    millis_ssl_2_0,
                    total_duration_in_ms
                    )
            """ + values(32);
    String id = Ulid.fast().toString();
    fullScan.setId(id);
    jdbcTemplate.update(
            insert,
            fullScan.getId(),
            timestamp(fullScan.getCrawlTimestamp()),
            fullScan.getIp(),
            fullScan.getServerName(),
            fullScan.isConnectOk(),
            fullScan.isSupportTls_1_3(),
            fullScan.isSupportTls_1_2(),
            fullScan.isSupportTls_1_1(),
            fullScan.isSupportTls_1_0(),
            fullScan.isSupportSsl_3_0(),
            fullScan.isSupportSsl_2_0(),
            fullScan.getSelectedCipherTls_1_3(),
            fullScan.getSelectedCipherTls_1_2(),
            fullScan.getSelectedCipherTls_1_1(),
            fullScan.getSelectedCipherTls_1_0(),
            fullScan.getSelectedCipherSsl_3_0(),
            null, // TODO: accepted_ciphers_ssl_2_0
            fullScan.getLowestVersionSupported(),
            fullScan.getHighestVersionSupported(),
            fullScan.getErrorTls_1_3(),
            fullScan.getErrorTls_1_2(),
            fullScan.getErrorTls_1_1(),
            fullScan.getErrorTls_1_0(),
            fullScan.getErrorSsl_3_0(),
            fullScan.getErrorSsl_2_0(),
            fullScan.getMillis_tls_1_3(),
            fullScan.getMillis_tls_1_2(),
            fullScan.getMillis_tls_1_1(),
            fullScan.getMillis_tls_1_0(),
            fullScan.getMillis_ssl_3_0(),
            fullScan.getMillis_ssl_2_0(),
            fullScan.getTotalDurationInMs()
    );
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
        save(pageVisit);

      }
    }
  }

}

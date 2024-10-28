package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.dns.dto.RecordType;
import be.dnsbelgium.mercator.dns.persistence.Request;
import be.dnsbelgium.mercator.dns.persistence.Response;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CrawlResultEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.FullScanEntity;
import be.dnsbelgium.mercator.tls.domain.certificates.Certificate;
import be.dnsbelgium.mercator.vat.crawler.persistence.PageVisit;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import com.github.f4b6a3.ulid.Ulid;
import eu.bosteels.mercator.mono.persistence.TableCreator;
import eu.bosteels.mercator.mono.persistence.VisitRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static be.dnsbelgium.mercator.tls.domain.certificates.CertificateReader.readTestCertificate;
import static be.dnsbelgium.mercator.test.TestUtils.now;
import static org.assertj.core.api.Assertions.assertThat;

class VisitRepositoryTest {

  static DuckDataSource dataSource;
  static VisitRepository visitRepository;
  static JdbcClient jdbcClient;

  @TempDir
  static File tempDir;

  private static final Logger logger = LoggerFactory.getLogger(VisitRepositoryTest.class);

  @BeforeAll
  public static void init() {
    dataSource = new DuckDataSource("jdbc:duckdb:");
    TableCreator tableCreator = new TableCreator(dataSource);
    tableCreator.init();
    tableCreator.createVisitTables();
    visitRepository = new VisitRepository(dataSource, tableCreator);
    visitRepository.setDatabaseDirectory(tempDir);
    visitRepository.setExportDirectory(tempDir);
    visitRepository.init();
    jdbcClient = JdbcClient.create(dataSource);
  }

  @Test
  @Transactional
  public void saveDnsCrawlResult() {
    Request request = new Request();
    request.setVisitId(VisitIdGenerator.generate());
    String requestId = Ulid.fast().toString();
    request.setId(requestId);
    request.setOk(true);
    request.setPrefix("www");
    request.setDomainName("google.be");
    request.setRecordType(RecordType.A);
    DnsCrawlResult crawlResult = DnsCrawlResult.of(List.of(request));
    visitRepository.save(crawlResult);
  }

  @Test
  public void insertResponse() {
    Request request = new Request();
    request.setVisitId(VisitIdGenerator.generate());
    String requestId = Ulid.fast().toString();
    String responseId = Ulid.fast().toString();
    request.setId(requestId);
    request.setOk(true);
    request.setPrefix("www");
    request.setDomainName("google.be");
    request.setRecordType(RecordType.A);
    Response response = new Response();
    response.setId(responseId);
    response.setTtl(3600L);
    response.setRecordData("IN 5 ns1.google.com");
    visitRepository.insertResponse(request, response);
  }

  @Test
  public void savePageVisit() {
    var vat_values = List.of("double quote \" here", "abc", "", "[0]{1}(2)'x'");
    PageVisit pageVisit = PageVisit.builder()
            .visitId(VisitIdGenerator.generate())
            .path("/example?id=455")
            .url("https://www.google.com")
            .bodyText("This is a test")
            .crawlFinished(now())
            .crawlStarted(now().minusMillis(456))
            .domainName("google.com")
            .vatValues(vat_values)
            .statusCode(0)
            .linkText("contact us")
            .build();
    visitRepository.save(pageVisit);
    List<PageVisit> pageVisits = visitRepository.findPageVisits(pageVisit.getVisitId());
    pageVisits.forEach(System.out::println);
    assertThat(pageVisits).hasSize(1);
    PageVisit found = pageVisits.get(0);
    assertThat(found.getVisitId()).isEqualTo(pageVisit.getVisitId());
    assertThat(found.getVatValues()).isEqualTo(pageVisit.getVatValues());
    assertThat(found.getHtml()).isEqualTo(pageVisit.getHtml());
    assertThat(found.getBodyText()).isEqualTo(pageVisit.getBodyText());
    assertThat(found.getUrl()).isEqualTo(pageVisit.getUrl());
    assertThat(found.getDomainName()).isEqualTo(pageVisit.getDomainName());
    assertThat(found.getLinkText()).isEqualTo(pageVisit.getLinkText());
    assertThat(found.getStatusCode()).isEqualTo(pageVisit.getStatusCode());
    assertThat(found.getPath()).isEqualTo(pageVisit.getPath());
    assertThat(found.getCrawlStarted()).isEqualTo(pageVisit.getCrawlStarted());
    assertThat(found.getCrawlFinished()).isEqualTo(pageVisit.getCrawlFinished());
    assertThat(found).isEqualTo(pageVisit);
  }


  @Test
  public void webVisit() {
    String visitId = VisitIdGenerator.generate();
    VatCrawlResult crawlResult = VatCrawlResult
            .builder()
            .visitId(visitId)
            .crawlStarted(now())
            .crawlFinished(now().plusMillis(126))
            .domainName("google.be")
            .visitedUrls(List.of("https://www.google.be", "https://google.com?countr=be"))
            .build();
    visitRepository.save(crawlResult);
    List<Map<String, Object>> rows = jdbcClient.sql("select * from web_visit").query().listOfRows();
    System.out.println("rows = " + rows);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("visit_id").toString()).isEqualTo(crawlResult.getVisitId());
    assertThat(rows.get(0).get("domain_name")).isEqualTo(crawlResult.getDomainName());

    Optional<VatCrawlResult> result = visitRepository.findVatCrawlResult(visitId);
    assertThat(result.isPresent()).isTrue();
    VatCrawlResult found = result.get();
    assertThat(found.getVisitId()).isEqualTo(visitId);
    assertThat(found.getVatValues()).isEqualTo(crawlResult.getVatValues());
    assertThat(found.getDomainName()).isEqualTo(crawlResult.getDomainName());
    assertThat(found.getCrawlStarted()).isEqualTo(crawlResult.getCrawlStarted());
    assertThat(found.getCrawlFinished()).isEqualTo(crawlResult.getCrawlFinished());
    assertThat(found.getVisitedUrls()).isEqualTo(crawlResult.getVisitedUrls());

  }

  @Test
  public void instantsInDuckdb() {
    Instant now = now();
    Timestamp timestamp = Timestamp.from(now);
    Instant instant = timestamp.toInstant();
    System.out.println("now       = " + now);
    System.out.println("timestamp = " + timestamp);
    System.out.println("instant   = " + instant);
    assertThat(instant).isEqualTo(now);
    DuckDataSource dataSource = new DuckDataSource("jdbc:duckdb:");
    JdbcClient jdbcClient = JdbcClient.create(dataSource);
    jdbcClient.sql("create table t1 (i timestamp)").update();
    jdbcClient
            .sql("insert into t1 (i) values (?)")
            .param(timestamp)
            .update();
    jdbcClient
            .sql("select * from t1")
            .query(rs -> {
              Timestamp ts = rs.getTimestamp("i");
              assertThat(ts).isEqualTo(timestamp);
              assertThat(ts.toInstant()).isEqualTo(instant);
            });
  }



  @Test
  public void certificate() throws CertificateException, IOException {
    X509Certificate x509Certificate = readTestCertificate("dnsbelgium.be.pem");
    Certificate certificate = Certificate.from(x509Certificate);
    logger.info("info = {}", certificate);
    logger.info("info = {}", certificate.prettyString());
    visitRepository.save(certificate.asEntity());
    // on conflict do nothing
    visitRepository.save(certificate.asEntity());
  }

  @Test
  void tls_crawl_result() {
    CertificateEntity certificateEntity = CertificateEntity.builder()
            .sha256fingerprint("12345")
            .build();

    visitRepository.save(certificateEntity);
    logger.info("certificateEntity = {}", certificateEntity);

    FullScanEntity fullScanEntity = FullScanEntity.builder()
            .serverName("dnsbelgium.be")
            .connectOk(true)
            .highestVersionSupported("TLS 1.3")
            .lowestVersionSupported("TLS 1.2")
            .supportTls_1_3(true)
            .supportTls_1_2(true)
            .supportTls_1_1(false)
            .supportTls_1_0(false)
            .supportSsl_3_0(false)
            .supportSsl_2_0(false)
            .errorTls_1_1("No can do")
            .errorTls_1_0("Go away")
            .errorSsl_3_0("Why?")
            .errorSsl_2_0("Protocol error")
            .ip("10.20.30.40")
            .crawlTimestamp(ZonedDateTime.now())
            .build();

    CrawlResultEntity crawlResultEntity = CrawlResultEntity.builder()
            .fullScanEntity(fullScanEntity)
            .domainName("dns.be")
            .hostName("www.dns.be")
            .visitId(Ulid.fast().toString())
            .crawlTimestamp(ZonedDateTime.now())
            .leafCertificateEntity(certificateEntity)
            .build();

    visitRepository.save(fullScanEntity);
    visitRepository.save(crawlResultEntity);
    logger.info("AFTER: crawlResultEntity = {}", crawlResultEntity);
  }

  @Test
  public void htmlFeatures() {
    HtmlFeatures htmlFeatures = new HtmlFeatures();
    htmlFeatures.visitId = VisitIdGenerator.generate();
    htmlFeatures.domainName = "google.com";
    htmlFeatures.crawlTimestamp = ZonedDateTime.now();
    htmlFeatures.body_text = "hello world";
    htmlFeatures.external_hosts = List.of("google.com", "facebook.com");
    htmlFeatures.linkedin_links = List.of("linkedin.com/abc", "https://linkedin.com/xxx");
    visitRepository.save(htmlFeatures);
  }

}
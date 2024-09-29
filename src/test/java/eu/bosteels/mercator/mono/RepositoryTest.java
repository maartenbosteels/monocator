package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.dns.dto.RecordType;
import be.dnsbelgium.mercator.dns.persistence.Request;
import be.dnsbelgium.mercator.dns.persistence.Response;
import be.dnsbelgium.mercator.dns.persistence.ResponseGeoIp;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CrawlResultEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.FullScanEntity;
import be.dnsbelgium.mercator.tls.domain.certificates.Certificate;
import be.dnsbelgium.mercator.vat.crawler.persistence.PageVisit;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import com.github.f4b6a3.ulid.Ulid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static be.dnsbelgium.mercator.tls.domain.certificates.CertificateReader.readTestCertificate;
import static java.time.Instant.now;

@SuppressWarnings("SqlDialectInspection")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test" })
@ContextConfiguration(classes = MonocatorApplication.class)
class RepositoryTest {

    @Autowired
    Repository repository;

    @Autowired
    TableCreator tableCreator;

    @Autowired DuckDataSource dataSource;

    private static final Logger logger = LoggerFactory.getLogger(RepositoryTest.class);

    @BeforeEach
    public void init() {
        tableCreator.init();
    }

    @Test
    public void findDone() {
        var list = repository.findDone("google.com");
        logger.info("list = {}", list);
    }

    @Test
    public void saveOperation() {
        repository.saveOperation(
                Instant.now(),
                "export database 'abc'",
                Duration.of(43, ChronoUnit.SECONDS)
        );
        // TODO: move to repository
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        var operations = jdbcTemplate.queryForList("select * from operations");
        logger.info("operations = {}", operations);
    }

    @Test
    public void saveDnsCrawlResult() {
        logger.info("repository = {}", repository);
        Request request = new Request();
        request.setVisitId(VisitIdGenerator.generate());
        request.setId(5000L);
        request.setOk(true);
        request.setPrefix("www");
        request.setDomainName("google.be");
        request.setRecordType(RecordType.A);
        DnsCrawlResult crawlResult = DnsCrawlResult.of(List.of(request));
        repository.save(crawlResult);
    }

    @Test
    @Disabled("first insert data")
    public void findDnsCrawlResultByVisitId() {
        Optional<DnsCrawlResult> crawlResult = repository.findDnsCrawlResultByVisitId_v2("8fc18d34-405a-442f-bb2a-9a157736af45");
        logger.info("crawlResult = {}", crawlResult);
        Long prev_request_id = null;
        Request currentRequest = null;

        Long prev_response_id = null;
        Response currentResponse = null;

        List<Request> results = new ArrayList<>();

        for (Request request : crawlResult.get().getRequests()) {
            logger.info("* request = {}", request);
            boolean belongsToPreviousRequest = request.getId().equals(prev_request_id);
            if (!belongsToPreviousRequest) {
                currentRequest = request;
                prev_request_id = request.getId();
                results.add(currentRequest);
            }
            for (Response response : request.getResponses()) {
                if (belongsToPreviousRequest && response.getId() != null) {
                    var responses = new ArrayList<>(currentRequest.getResponses());
                    responses.add(response);
                    currentRequest.setResponses(responses);
                }

                boolean geoIpBelongsToPreviousResponse = Objects.equals(response.getId(), prev_response_id);
                if (!geoIpBelongsToPreviousResponse) {
                    currentResponse = response;
                    prev_response_id = response.getId();
                }
                logger.info("  **  response = {}", response);
                for (ResponseGeoIp geoIp : response.getResponseGeoIps()) {
                    logger.info("     *** geoIp = {}", geoIp);

                    if (geoIpBelongsToPreviousResponse) {
                        var geoIps = new ArrayList<>(currentResponse.getResponseGeoIps());
                        geoIps.add(geoIp);
                        currentResponse.setResponseGeoIps(geoIps);
                    }
                }
            }
        }

        logger.info("results = {}", results.size());

        boolean equal = results.equals(crawlResult.get().getRequests());
        logger.info("equal = {}", equal);
        // now reduce this list: group by responses if request.id is the same


    }


    @Test
    public void savePageVisit() {
        PageVisit pageVisit = PageVisit.builder()
                .visitId(VisitIdGenerator.generate())
                .path("/example?id=455")
                .url("https://www.google.com")
                .bodyText("This is a test")
                .crawlFinished(now())
                .crawlStarted(now().minusMillis(456))
                .domainName("google.com")
                .vatValues(List.of("double quote \" here", "abc", "", "[0]{1}(2)'x'"))
                .statusCode(0)
                .linkText("contact us")
                .build();
        repository.save(pageVisit);
        // TODO add asserts
    }

    @Test
    public void webVisit() {
        VatCrawlResult crawlResult = VatCrawlResult
                .builder()
                .visitId(VisitIdGenerator.generate())
                .crawlStarted(now())
                .crawlFinished(now().plusMillis(126))
                .domainName("google.be")
                .visitedUrls(List.of("https://www.google.be", "https://google.com?countr=be"))
                .build();
        repository.save(crawlResult);
    }

    @Test
    public void certificate() throws CertificateException, IOException {
        X509Certificate x509Certificate = readTestCertificate("dnsbelgium.be.pem");
        Certificate certificate = Certificate.from(x509Certificate);
        logger.info("info = {}", certificate);
        logger.info("info = {}", certificate.prettyString());
        repository.save(certificate.asEntity());
        // on conflict do nothing
        repository.save(certificate.asEntity());
    }

    @Test
    void tls_crawl_result() {
        CertificateEntity certificateEntity = CertificateEntity.builder()
                .sha256fingerprint("12345")
                .build();

        repository.save(certificateEntity);
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

        repository.save(fullScanEntity);
        repository.save(crawlResultEntity);
        logger.info("AFTER: crawlResultEntity = {}", crawlResultEntity);
    }

    @Test
    public void getRecentCrawlRates() {
        var rates = repository.getRecentCrawlRates(Repository.Frequency.PerHour, 100);
        for (CrawlRate rate : rates) {
            System.out.println("rate = " + rate);
        }
    }

    @Test
    public void getRecentCrawlRatesPerMinute() {
        var rates = repository.getRecentCrawlRates(Repository.Frequency.PerMinute, 100);
        for (CrawlRate rate : rates) {
            System.out.println("rate = " + rate);
        }
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
        repository.save(htmlFeatures);
    }

    @Test
    public void timestamp() {
        var jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("create or replace table ts (id int, ts timestamptz)");
        jdbcTemplate.execute("insert into ts (id, ts) values (1, current_timestamp )");
        jdbcTemplate.execute("insert into ts (id, ts) values (2, '2024-07-21 13:25:50.45+02' )");
        jdbcTemplate.update(
                "insert into ts (id, ts) values (?, ? )",
                3,
                "2024-07-21 13:25:50.45+02"
                );
        Instant dt = now();
        var local = dt.atZone(ZoneId.of("Europe/Brussels"));

        final long millis = dt.toEpochMilli();
        logger.info("millis = {}", millis);

        logger.info("dt    = {}", dt);
        logger.info("local = {}", local);

        jdbcTemplate.update(
                "insert into ts (id, ts) values (?, epoch_ms(?) )",
                ps -> {
                    ps.setLong(1, 4);
                    ps.setLong(2, 1000 * local.toEpochSecond());
                }
        );

        jdbcTemplate.update(
                "insert into ts (id, ts) values (?, ? )",
                ps -> {
                    ps.setLong(1, 60);
                    ps.setTimestamp(2, new Timestamp(millis));
                }
        );

        jdbcTemplate.update(
                "insert into ts (id, ts) values (?, ? )",
                70, new Timestamp(millis)
        );

        var list = jdbcTemplate.queryForList("select * from ts");
        //logger.info("list = {}", Arrays.to);
        for (Map<String, Object> objectMap : list) {
            logger.info(objectMap.toString());
        }


    }


}
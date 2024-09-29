package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;

import be.dnsbelgium.mercator.dns.dto.RecordType;
import be.dnsbelgium.mercator.dns.persistence.Request;
import be.dnsbelgium.mercator.dns.persistence.Response;
import be.dnsbelgium.mercator.dns.persistence.ResponseGeoIp;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CrawlResultEntity;
import be.dnsbelgium.mercator.tls.crawler.persistence.entities.FullScanEntity;
import be.dnsbelgium.mercator.vat.crawler.persistence.PageVisit;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


@SuppressWarnings({"SqlDialectInspection", "SqlSourceToSinkFlow"})
@Component
public class Repository {

    private static final Logger logger = LoggerFactory.getLogger(Repository.class);
    private final JdbcTemplate jdbcTemplate;

    public enum Frequency {PerMinute, PerHour}

    @Autowired
    public Repository(DuckDataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public Repository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long nextid() {
        return jdbcTemplate.queryForObject("select nextval('serial')", Long.class);
    }

    public void saveOperation(Instant ts, String sql, Duration duration) {
        String insert = "insert into operations(ts, sql, millis) values (?,?,?)";
        jdbcTemplate.update(insert, Timestamp.from(ts), sql, duration.toMillis());
    }

    public List<Done> findDone(String domainName) {
        return jdbcTemplate.query(
                """
                    select visit_id, domain_name, done
                    from done
                    where domain_name = ?
                    order by done desc
                """,
                (rs, rowNum) -> {
                    var visitId = rs.getString("visit_id");
                    var domain_name = rs.getString("domain_name");
                    var done = instant(rs.getTimestamp("done"));
                    return new Done(visitId, domain_name, done);
                }, domainName);
    }

    public void markDone(VisitRequest visitRequest) {
        logger.debug("Marking as done: {}", visitRequest);
        jdbcTemplate.update(
                "insert into done(visit_id, domain_name, done) values(?,?,current_timestamp)",
                visitRequest.getVisitId(), visitRequest.getDomainName()
        );
        jdbcTemplate.update("delete from work where visit_id = ?", visitRequest.getVisitId());
    }

    public void save(@NotNull DnsCrawlResult crawlResult) {
        logger.info("Saving crawlResult = {}", crawlResult.getStatus());
        for (var req : crawlResult.getRequests()) {
            Long id = nextid();
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

    public Optional<DnsCrawlResult> findDnsCrawlResultByVisitId(String visitId) {
        var select = """
                select
                    dns_response.id as response_id,
                    dns_request.id as request_id,
                    *
                from dns_request
                left join dns_response on dns_response.dns_request	= dns_request.id
                left join response_geo_ips on response_geo_ips.dns_response = dns_response.id
                where visit_id = ?
                order by request_id, response_id, ip
                """;
        var requests = jdbcTemplate.query(select,
                (rs, rowNum) -> {
                    Long response_id = getLong(rs, "response_id");
                    Long request_id = getLong(rs,"request_id");
                    List<ResponseGeoIp> geoIps = List.of();
                    Long asn = getLong(rs, "asn");
                    if (asn != null) {
                        String asn_org = rs.getString("asn_organisation");
                        String ip = rs.getString("ip");
                        String country = rs.getString("country");
                        int ip_version = rs.getInt("ip_version");
                        geoIps = List.of(new ResponseGeoIp(Pair.of(asn, asn_org), country, ip_version, ip));
                    }
                    String record_data	= rs.getString("record_data");
                    Long ttl = getLong(rs, "ttl");
                    var response = new Response(response_id, record_data, ttl, geoIps);

                    String rtype = rs.getString("record_type");
                    Timestamp ts = rs.getTimestamp("crawl_timestamp");
                    ZonedDateTime crawl_timestamp = zonedDateTime(ts, ZoneId.systemDefault());

                    return Request.builder()
                            .id(request_id)
                            .domainName(rs.getString("domain_name"))
                            .rcode(rs.getInt("rcode"))
                            .problem(rs.getString("problem"))
                            .prefix(rs.getString("prefix"))
                            .recordType(RecordType.valueOf(rtype))
                            .crawlTimestamp(crawl_timestamp)
                            .responses(List.of(response))
                            .build();
                },
                visitId);

        if (requests.isEmpty()) {
            return Optional.empty();
        }
        var request = DnsCrawlResult.of(requests);
        logger.info("findDnsCrawlResultByVisitId: {}", request);
        return Optional.of(request);
    }
public Optional<DnsCrawlResult> findDnsCrawlResultByVisitId_v2(String visitId) {
        var select = """
                select
                    dns_response.id as response_id,
                    dns_request.id as request_id,
                    *
                from dns_request
                left join dns_response on dns_response.dns_request	= dns_request.id
                left join response_geo_ips on response_geo_ips.dns_response = dns_response.id
                where visit_id = ?
                order by request_id, response_id, ip
                """;

        var rowCallbackHandler = new DnsResultsRowCallbackHandler();
        jdbcTemplate.query(select, rowCallbackHandler, visitId);
        return rowCallbackHandler.getDnsCrawlResult();
    }

    @SuppressWarnings("unused")
    private static Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    public static Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        if (rs.wasNull()) {
            return null;
        }
        return value;
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
        Long id = nextid();
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

    public void save(@NotNull PageVisit pageVisit) {
        Long id = nextid();
        logger.debug("Saving PageVisit with id={} and url={}", id, pageVisit.getUrl());
        pageVisit.setId(id);
        String insert = """
                insert into web_page_visit (
                    id,
                    visit_id,
                    domain_name,
                    crawl_started,
                    crawl_finished,
                    html,
                    body_text,
                    status_code,
                    url,
                    path,
                    link_text,
                    vat_values
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,
                        list_transform(?::varchar[], s -> replace(s, '"', ''))
                    )
                """;
        jdbcTemplate.update(
                insert,
                pageVisit.getId(),
                pageVisit.getVisitId(),
                pageVisit.getDomainName(),
                new Timestamp( pageVisit.getCrawlStarted().toEpochMilli() ),
                new Timestamp( pageVisit.getCrawlFinished().toEpochMilli() ),
                pageVisit.getHtml(),
                pageVisit.getBodyText(),
                pageVisit.getStatusCode(),
                pageVisit.getUrl(),
                pageVisit.getPath(),
                pageVisit.getLinkText(),
                asList(pageVisit.getVatValues())
        );
    }

    public void save(@NotNull VatCrawlResult crawlResult) {
        var insert = """
                insert into web_visit
                (
                    id,
                    visit_id,
                    domain_name,
                    start_url,
                    matching_url,
                    crawl_started,
                    crawl_finished,
                    visited_urls
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        var id = nextid();
        crawlResult.setId(id);
        int rowsInserted = jdbcTemplate.update(
                insert,
                crawlResult.getId(),
                crawlResult.getVisitId(),
                crawlResult.getDomainName(),
                crawlResult.getStartUrl(),
                crawlResult.getMatchingUrl(),
                timestamp(crawlResult.getCrawlStarted()),
                timestamp(crawlResult.getCrawlFinished()),
                asList(crawlResult.getVisitedUrls())
        );
        logger.debug("domain={} rowsInserted={}", crawlResult.getDomainName(), rowsInserted);
    }

    public void save(@NotNull HtmlFeatures h) {
        var insert = """
                insert into html_features(
                    id,
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
                     + values(54);
        h.id = nextid();
        jdbcTemplate.update(insert,
                h.id,
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
                asList(h.external_hosts),
                h.nb_facebook_deep_links,
                h.nb_facebook_shallow_links,
                h.nb_linkedin_deep_links    ,
                h.nb_linkedin_shallow_links ,
                h.nb_twitter_deep_links     ,
                h.nb_twitter_shallow_links  ,
                h.nb_currency_names         ,
                h.nb_distinct_currencies    ,
                h.distance_title_final_dn   ,
                h.longest_subsequence_title_final_dn,
                asList(h.facebook_links),
                asList(h.twitter_links),
                asList(h.linkedin_links),
                asList(h.youtube_links),
                asList(h.vimeo_links),
                h.nb_youtube_deep_links     ,
                h.nb_youtube_shallow_links  ,
                h.nb_vimeo_deep_links       ,
                h.nb_vimeo_shallow_links    ,
                h.body_text_language        ,
                h.body_text_language_2      ,
                h.fraction_words_title_initial_dn,
                h.fraction_words_title_final_dn  ,
                h.nb_distinct_words_in_title,
                h.distance_title_initial_dn ,
                h.longest_subsequence_title_initial_dn
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
        fullScan.setId(nextid());
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

    @SuppressWarnings("DataFlowIssue")
    public Stats getStats() {
        long todo = jdbcTemplate.queryForObject("select count(1) from work", Long.class);
        long done = jdbcTemplate.queryForObject("select count(1) from done", Long.class);
        return new Stats(todo, done);
    }

    private record CrawlRateMapper(Frequency frequency) implements RowMapper<CrawlRate> {

        @Override
            public CrawlRate mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp ts = rs.getTimestamp("ts");
                var when = zonedDateTime(ts, ZoneId.systemDefault());
                int count = rs.getInt("count");
                if (Objects.requireNonNull(frequency) == Frequency.PerHour) {
                    return new CrawlRate(when, count, count / 60.0, count / 3600.0);
                }
                return new CrawlRate(when, count * 60.0, count, count / 60.0);
            }
        }

    public List<CrawlRate> getRecentCrawlRates(Frequency frequency, int limit) {
        CrawlRateMapper mapper = new CrawlRateMapper(frequency);
        var select = String.format("""
            select
                date_trunc('%s', done) as ts,
                count(1) as count
            from done
            group by ts
            order by ts desc
            limit %s
            """, toSqlPrecision(frequency), limit);
        return jdbcTemplate.query(select, mapper);
    }

    private String toSqlPrecision(Frequency frequency) {
        return switch (frequency) {
            case PerHour -> "hour";
            case PerMinute -> "minute";
        };
    }

    public void save(CrawlResultEntity crawlResult) {
        String insert = """
                    insert into tls_crawl_result(
                    id,
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
                """ + values (11);
        crawlResult.setId(nextid());
        var leafCert = (crawlResult.getLeafCertificateEntity() == null) ? null : crawlResult.getLeafCertificateEntity().getSha256fingerprint();
        jdbcTemplate.update(
                insert,
                crawlResult.getId(),
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
                asList(certificate.getSubjectAltNames()),
                //asList(certificate.getSubjectAlternativeNames()),
                timestamp(certificate.getNotBefore()),
                timestamp(certificate.getNotAfter())
                );
    }

    public static Timestamp timestamp(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return Timestamp.from(zonedDateTime.toInstant());
    }

    static ZonedDateTime zonedDateTime(Timestamp timestamp, ZoneId zoneId) {
        if (timestamp == null) {
            return null;
        }
        return ZonedDateTime.of(timestamp.toLocalDateTime(), zoneId);
    }

    public static Timestamp timestamp(Instant instant) {
        if (instant == null) {
          return null;
        }
        return Timestamp.from(instant);
    }

    public static Instant instant(Timestamp timestamp) {
        return (timestamp == null) ? null : timestamp.toInstant();
    }

    // creates a string that duckdb can parse/convert to a varchar[]
    public static String asList(List<String> list) {
        // We put quotes around every element in the list
        // Any double quotes in the elements of list will be removed.
        // Use list_transform to remove the quotes after casting to varchar[]

        boolean containsDoubleQuotes = list.stream().anyMatch(s -> s.contains("\""));
        if (containsDoubleQuotes) {
           logger.warn("""
                You are trying to save a varchar array that contains double quotes.
                Double quotes will be removed before saving the list to the database.
                Input list was {}
                """, list);
        }
        String result = list.stream()
                .map(s -> s.replaceAll("\"", ""))
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        logger.info("{} => {}", list, result);
        return result;

    }

    public String values(int paramCount) {
        return " values( " + StringUtils.repeat("?", ", ", paramCount) + ")";
    }

}

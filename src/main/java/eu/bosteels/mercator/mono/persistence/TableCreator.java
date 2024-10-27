package eu.bosteels.mercator.mono.persistence;

import be.dnsbelgium.mercator.DuckDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@SuppressWarnings("SqlDialectInspection")
@Component
public class TableCreator {

    private final JdbcTemplate template;
    private static final Logger logger = LoggerFactory.getLogger(TableCreator.class);

    @Autowired
    public TableCreator(DuckDataSource dataSource) {
        this.template = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void init() {
        createWorkTables();
    }

    public void createVisitTables() {
        createSequence();
        createTablesDns();
        createTablesWeb();
        createTableFeatures();
        createTablesTls();
        blacklistEntry();
    }

    private void createWorkTables() {
        createSequence();
        var ddl_work = "create table if not exists work (visit_id varchar, domain_name varchar)";
        execute(ddl_work);
        var ddl_done = "create table if not exists done (visit_id varchar, domain_name varchar, done timestamp)";
        execute(ddl_done);
        var ddl_ingested = """
                create table if not exists ingested (
                    ingestion_id    bigint,
                    visit_id        varchar,
                    domain_name     varchar,
                    filename        varchar,
                    ingested_at     timestamp
                )""";
        execute(ddl_ingested);

        var ddl_operations = """
                create table if not exists operations (
                    ts      timestamp,
                    sql     varchar,
                    millis  bigint
                )
                """;
        execute(ddl_operations);
    }

    private void blacklistEntry() {
        var ddl_blacklist = """
            create table if not exists blacklist_entry(
                cidr_prefix varchar(256)    primary key
            )
            """;
        execute(ddl_blacklist);
    }

    private void execute(String sql) {
        logger.info("Start executing sql = {}", sql);
        template.execute(sql);
        logger.info("Done executing sql = {}", sql);
    }

    public void createSequence() {
        execute("CREATE SEQUENCE if not exists serial START 1");
    }

    public void createTablesDns() {
        var ddl_request = """
                create table if not exists dns_request
                (
                    id               varchar(26)              primary key,
                    visit_id         varchar(26)              not null,
                    domain_name      varchar(128)             not null,
                    prefix           varchar(63)              not null,
                    record_type      char(10)                 not null,
                    rcode            integer,
                    crawl_timestamp  timestamp                not null,
                    ok               boolean,
                    problem          text,
                    num_of_responses integer                  not null
                )
                """;
        execute(ddl_request);
        var ddl_response = """
                create table if not exists dns_response
                (
                    id                  varchar         primary key,
                    dns_request         varchar         not null,
                    record_data         text            not null,
                    ttl                 integer
                )
                """;
        execute(ddl_response);
        var geo = """
                create table if not exists response_geo_ips
                (
                    dns_response     varchar,
                    asn              varchar(255),
                    country          varchar(255),
                    ip               varchar(255),
                    asn_organisation varchar(128),
                    ip_version       integer not null
                )
                """;
        execute(geo);
    }

    public void createTablesWeb() {
        var ddl_web_page_visit = """
                create table if not exists web_page_visit
                (
                    visit_id       varchar(26)              not null,
                    domain_name    varchar(255),
                    crawl_started  timestamp,
                    crawl_finished timestamp,
                    html           text,
                    body_text      text,
                    status_code    integer,
                    url            varchar(500),
                    path           varchar(500),
                    vat_values     varchar[],
                    link_text      varchar(500)
                )
                """;
        execute(ddl_web_page_visit);
        var ddl_web_visit = """
                create table if not exists web_visit
                (
                    visit_id       varchar(26),
                    domain_name    varchar(255),
                    start_url      varchar(255),
                    matching_url   varchar(255),
                    crawl_started  timestamp,
                    crawl_finished timestamp,
                    visited_urls   varchar[]
                )
                """;
        execute(ddl_web_visit);
    }

    public void createTableFeatures() {
        String ddl_html_features = """
                create table if not exists html_features
                (
                    visit_id                             varchar(26)              not null,
                    crawl_timestamp                      timestamp                not null,
                    domain_name                          varchar(128)             not null,
                    html_length                          bigint,
                    nb_imgs                              integer,
                    nb_links_int                         integer,
                    nb_links_ext                         integer,
                    nb_links_tel                         integer,
                    nb_links_email                       integer,
                    nb_input_txt                         integer,
                    nb_button                            integer,
                    nb_meta_desc                         integer,
                    nb_meta_keyw                         integer,
                    nb_numerical_strings                 integer,
                    nb_tags                              integer,
                    nb_words                             integer,
                    title                                varchar(2000),
                    htmlstruct                           varchar(2000),
                    body_text                            text,
                    meta_text                            text,
                    url                                  varchar(255),
                    body_text_truncated                  boolean,
                    meta_text_truncated                  boolean,
                    title_truncated                      boolean,
                    nb_letters                           integer,
                    nb_distinct_hosts_in_urls            integer,
                    external_hosts                       varchar[],
                    nb_facebook_deep_links               integer,
                    nb_facebook_shallow_links            integer,
                    nb_linkedin_deep_links               integer,
                    nb_linkedin_shallow_links            integer,
                    nb_twitter_deep_links                integer,
                    nb_twitter_shallow_links             integer,
                    nb_currency_names                    integer,
                    nb_distinct_currencies               integer,
                    distance_title_final_dn              integer,
                    longest_subsequence_title_final_dn   integer,
                    facebook_links                       varchar[],
                    twitter_links                        varchar[],
                    linkedin_links                       varchar[],
                    youtube_links                        varchar[],
                    vimeo_links                          varchar[],
                    nb_youtube_deep_links                integer,
                    nb_youtube_shallow_links             integer,
                    nb_vimeo_deep_links                  integer,
                    nb_vimeo_shallow_links               integer,
                    body_text_language                   varchar(128),
                    body_text_language_2                 varchar(128),
                    fraction_words_title_initial_dn      double precision,
                    fraction_words_title_final_dn        double precision,
                    nb_distinct_words_in_title           integer,
                    distance_title_initial_dn            integer,
                    longest_subsequence_title_initial_dn integer,
                )
                """;
        execute(ddl_html_features);

    }

    public void createTablesTls() {
        var ddl_certificate = """
                create table if not exists tls_certificate
                (
                    sha256_fingerprint       varchar(256) primary key,
                    version                  integer      not null,
                    public_key_schema        varchar(256),
                    public_key_length        integer,
                    not_before               timestamp,
                    not_after                timestamp,
                    issuer                   varchar(500),
                    subject                  varchar(500),
                    signature_hash_algorithm varchar(256),
                    signed_by_sha256         varchar(256)  references tls_certificate,
                    subject_alt_names        varchar[],
                    serial_number_hex        varchar(64),
                    insert_timestamp         timestamp default CURRENT_TIMESTAMP
                )
                """;
        execute(ddl_certificate);
        var ddl_full_scan = """
                create table if not exists tls_full_scan
                (
                    id                        varchar                     primary key,
                    crawl_timestamp           timestamp                   not null,
                    ip                        varchar(255),
                    server_name               varchar(128)                not null,
                    connect_ok                boolean                     not null,
                    support_tls_1_3           boolean,
                    support_tls_1_2           boolean,
                    support_tls_1_1           boolean,
                    support_tls_1_0           boolean,
                    support_ssl_3_0           boolean,
                    support_ssl_2_0           boolean,
                    selected_cipher_tls_1_3   varchar,
                    selected_cipher_tls_1_2   varchar,
                    selected_cipher_tls_1_1   varchar,
                    selected_cipher_tls_1_0   varchar,
                    selected_cipher_ssl_3_0   varchar,
                    accepted_ciphers_ssl_2_0  varchar[],
                    lowest_version_supported  varchar,
                    highest_version_supported varchar,
                    error_tls_1_3             varchar,
                    error_tls_1_2             varchar,
                    error_tls_1_1             varchar,
                    error_tls_1_0             varchar,
                    error_ssl_3_0             varchar,
                    error_ssl_2_0             varchar,
                    millis_ssl_2_0            integer,
                    millis_ssl_3_0            integer,
                    millis_tls_1_0            integer,
                    millis_tls_1_1            integer,
                    millis_tls_1_2            integer,
                    millis_tls_1_3            integer,
                    total_duration_in_ms      integer
                )
                """;
        execute(ddl_full_scan);
        var ddl_tls_crawl_result = """
                create table if not exists tls_crawl_result
                (
                    visit_id                       varchar(26)                     not null,
                    domain_name                    varchar(128)                    not null,
                    crawl_timestamp                timestamp                       not null,
                    full_scan                      varchar                         not null  references tls_full_scan,
                    host_name_matches_certificate  boolean,
                    host_name                      varchar(128)                    not null,
                    leaf_certificate               varchar(256),
                    certificate_expired            boolean,
                    certificate_too_soon           boolean,
                    chain_trusted_by_java_platform boolean
                )
                """;
        execute(ddl_tls_crawl_result);
    }
}

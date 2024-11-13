package eu.bosteels.mercator.mono.persistence;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.smtp.SmtpCrawler;
import be.dnsbelgium.mercator.tls.ports.TlsCrawler;
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

    private final SmtpCrawler smtpCrawler;
    private final TlsCrawler tlsCrawler;

    @Autowired
    public TableCreator(DuckDataSource dataSource, SmtpCrawler smtpCrawler, TlsCrawler tlsCrawler) {
        this.template = new JdbcTemplate(dataSource);
        this.smtpCrawler = smtpCrawler;
        this.tlsCrawler = tlsCrawler;
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
        blacklistEntry();
        // ugly null checks until we have refactored all crawlers to CrawlerModule
        if (smtpCrawler != null) {
            smtpCrawler.createTables();
        }
        if (tlsCrawler != null) {
            tlsCrawler.createTables();
        }
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
        logger.info("Done executing sql {}", sql);
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

}

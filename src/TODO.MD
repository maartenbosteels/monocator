# 19/10
* test tls + caching
* smtp
* revert ZonedDateTime to Instant
* Crawler interface + implementations
* clean up visits_db files at the end of a test (or use temp folder)


# DDL: smtp todo

# insert methods: smtp todo
* smtp_conversation
* smtp_visit
* smtp_host

# full crawl: smtp still todo

# split up Repository class
* SchedulerRepository for tables Done, Todo, Ingested
* store locations of Visit databases in scheduler db
* on startup: check if previous VisitDatabase still exists


# report duckdb bug
create table t as select current_timestamp ct, epoch_ms(current_timestamp) ems, make_timestamp(epoch_ms(current_timestamp)*1000) mt, epoch_ms(epoch_ms(current_timestamp)) ct2;

set time zone 'UTC' 
ct = ct2 => True

set time zone 'Europe/Brussels'
ct = ct2 => False

# Fix language detection

# Reduce overlap html_features vs web_page_visit

# testing
 
## Tests on 15/9/2024 (with network)
* 173 passed
* 0 failed
* 81 ignored


## make tests faster ?
Now all tests are sub-second, except
* PageFetcherTest 4.2s             => tries to fetch non-existing url from dnsbelgium.be 
* TlsCrawlerApplicationTests: 2.6s => does a real TLS crawl on google.be
* MxFinderTest: 1s
* disable activemq during tests

## MaxMind: DONE
All tests should pass WITHOUT trying to download maxmind DB

## integration tests
* 6 Test classes that depend on the duckdb database, hence on the app context
* give these a tag/label so that we can exclude them ?


## clean up tests
* remove unused classes
* remove unused methods
* ContentCrawlResultTest
* ContentResolutionTest => what does it test ?

## review disabled tests

## ULID
* use VisitIdGenerator everywhere (instead of Ulid)
* create a VisitId class instead of String ?

## PlayWright

# replace ASN from Long to String, since we don't count with it ? nth

# clean up
* remove unused classes
* remove unused deps
* rename packages
* rename classes

### remove deps
* remove com.zaxxer:HikariCP:5.1.0

# check test coverage
* now: 73% class, 70% method, 65% line

# VisitRequest Message format
* dns.enabled
* dns.prefixes
* dns.rrtypes
* web.enabled
* web.max_pages
* screenshot
  * always
  * if_single_page_app
  * if_html_changed
  * if_developed
  * if_suspicious
* vat.enabled
* tls.enabled
* tls.tls13.enabled
* tls.tls12.enabled
* tls.tls11.enabled
* tls.tls10.enabled
* tls.ssl2.enabled
* tls.ssl3.enabled
* smtp.enabled

# Wappalyzer

# PlayWright with chrome
* Har file
* Screenshot
* html

# Replace com.hubspot.smtp.client with other lib

# Mappers
* code to retrieve data/objects from duckdb
* for displaying in UI
* alternative: show raw data in UI + some interactivity where needed
  * collapse/expand lists
  * show screenshot
  * show html in new tab
  * prettify certificate ...
  * link to KBO

# Maven
* migrate from gradle to maven => nice to have

# Build docker image
* with jib or with spring boot something ?

# Web UI

## Upload visit requests
## Show visit
## summary of crawls
* activemq stats
* sqs stats
* crawls:
  * total per TLD
  * total per TLD per month
  * total last 24h
  * total last hour
## package be.dnsbelgium.mercator.tls.crawler.persistence.entities

### FullScanEntity
* simple class using lombok (getter & builder) 
* used in FullScanCache, TlsCrawler, CrawlResult and CrawlResultEntity

### CrawlResultEntity
* simple class using lombok (getter & builder)
* used in CrawlResult#convertToEntity and VisitRepository

### BlacklistEntry
* used in BlackList and BlackListRepository
* simple class with 1 String field

## CertificateEntity
* lombok Builder/Getter/...
* used in CrawlResultEntity, CrawlResult, Certificate, VisitRepository & VisitRepositoryTest

--------------------------------------------------
## package be.dnsbelgium.mercator.tls.domain 

###  CrawlResult
* used in MainCrawler, TlsCrawler and VisitRepository
* has method to convert to FullScanEntity 

### FullScan
* Map<TlsProtocolVersion, SingleVersionScan> scanPerVersionMap
* boolean connectOK
* used in FullScanCache, CrawlResult, TlsScanner and TlsCrawler


### SingleVersionScan
* @Data
* used in FullScan, CrawlResult and TlsScanner

# Flow of TLS crawler

* MainCrawler.collectData(visitRequest)
* TlsCrawler.visit(visitRequest)
* CrawlResult visit(String hostName, VisitRequest visitRequest)
* FullScanEntity fullScan =  fullScanCache.find(ip)
* tlsScanner.scan(address)

* VisitService.save(VisitResult)
* visitRepository.save(visitResult);
* for (CrawlResult tlsCrawlResult : visitResult.tlsCrawlResult().crawlResults())
* visitRepository.persist(CrawlResult tlsCrawlResult)
* tlsCrawlResult.convertToEntity()
* save(FullScanEntity fullScan)
* saveCertificates(Optional<List<Certificate>> chain)
* save(crawlResultEntity)




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


package eu.bosteels.mercator.mono.visits;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.dns.CrawlStatus;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlService;
import be.dnsbelgium.mercator.feature.extraction.HtmlFeatureExtractor;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.smtp.SmtpCrawler;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.tls.domain.TlsCrawlResult;
import be.dnsbelgium.mercator.tls.ports.TlsCrawler;
import be.dnsbelgium.mercator.vat.VatCrawlerService;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import be.dnsbelgium.mercator.vat.domain.Page;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;
import eu.bosteels.mercator.mono.metrics.Threads;
import eu.bosteels.mercator.mono.persistence.Repository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static be.dnsbelgium.mercator.vat.metrics.MetricName.COUNTER_WEB_CRAWLS_DONE;

@SuppressWarnings("SqlDialectInspection")
@Service
public class MainCrawler {

  private final DnsCrawlService dnsCrawlService;
  private final VatCrawlerService vatCrawlerService;
  private final TlsCrawler tlsCrawler;
  private final HtmlFeatureExtractor htmlFeatureExtractor;
  private final SmtpCrawler smtpCrawler;
  private final MeterRegistry meterRegistry;

  private final Repository repository;
  private final VisitService visitService;

  private final List<CrawlerModule<?>> crawlerModules;

  @Value("${smtp.enabled:true}")
  private boolean smtpEnabled;

  private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);

  @Autowired
  public MainCrawler(DnsCrawlService dnsCrawlService,
                     VatCrawlerService vatCrawlerService,
                     HtmlFeatureExtractor htmlFeatureExtractor,
                     Repository repository,
                     SmtpCrawler smtpCrawler,
                     TlsCrawler tlsCrawler,
                     MeterRegistry meterRegistry,
                     VisitService visitService) {
    this.dnsCrawlService = dnsCrawlService;
    this.vatCrawlerService = vatCrawlerService;
    this.tlsCrawler = tlsCrawler;
    this.htmlFeatureExtractor = htmlFeatureExtractor;
    this.repository = repository;
    this.meterRegistry = meterRegistry;
    this.visitService = visitService;
    this.smtpCrawler = smtpCrawler;
    crawlerModules = new ArrayList<>();
  }

  @SuppressWarnings("unused")
  public void register(CrawlerModule<?> crawlerModule) {
    crawlerModules.add(crawlerModule);
  }

  public void visit(VisitRequest visitRequest) {
    VisitResult visitResult = collectData(visitRequest);
    visitService.save(visitResult);
    repository.markDone(visitRequest);
    postSave(visitResult);
  }

  private void postSave(VisitResult visitResult) {
    Threads.POST_SAVE.incrementAndGet();
    try {

      var dataPerModule = visitResult.getCollectedData();
      for (CrawlerModule<?> crawlerModule : dataPerModule.keySet()) {
        var data = dataPerModule.get(crawlerModule);
        crawlerModule.afterSave(data);
      }

    } finally {
      Threads.POST_SAVE.decrementAndGet();
    }
  }

  private List<HtmlFeatures> findFeatures(VisitRequest visitRequest, SiteVisit siteVisit) {
    Threads.FEATURE_EXTRACTION.incrementAndGet();
    try {
      logger.info("siteVisit = {}", siteVisit);
      List<HtmlFeatures> featuresList = new ArrayList<>();
      for (Page page : siteVisit.getVisitedPages().values()) {
        var html = page.getDocument().html();
        logger.info("page.url = {}", page.getUrl());
        var features = htmlFeatureExtractor.extractFromHtml(
                html,
                page.getUrl().url().toExternalForm(),
                visitRequest.getDomainName()
        );
        features.visitId = visitRequest.getVisitId();
        features.crawlTimestamp = ZonedDateTime.now();
        features.domainName = visitRequest.getDomainName();
        featuresList.add(features);
      }
      return featuresList;
    } finally {
      Threads.FEATURE_EXTRACTION.decrementAndGet();
    }
  }

  private VisitResult collectData(VisitRequest visitRequest) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      DnsCrawlResult dnsCrawlResult = dnsCrawlService.visit(visitRequest);
      if (dnsCrawlResult.getStatus() == CrawlStatus.NXDOMAIN) {
        return VisitResult.builder()
                .visitRequest(visitRequest)
                .dnsCrawlResult(dnsCrawlResult)
                .build();
      }
      Map<CrawlerModule<?>, List<?>> collectedData = new HashMap<>();

      SiteVisit siteVisit = vatCrawlerService.visit(visitRequest);
      VatCrawlResult vatCrawlResult = vatCrawlerService.convert(visitRequest, siteVisit);
      meterRegistry.counter(COUNTER_WEB_CRAWLS_DONE).increment();
      List<HtmlFeatures> featuresList = findFeatures(visitRequest, siteVisit);

      List<TlsCrawlResult> tlsCrawlResults = tlsCrawler.collectData(visitRequest);
      collectedData.put(tlsCrawler, tlsCrawlResults);

      if (smtpEnabled) {
        logger.info("crawling SMTP for {}", visitRequest.getDomainName());
        List<SmtpVisit> smtpVisits = smtpCrawler.collectData(visitRequest);
        logger.info("DONE crawling SMTP for {} => {}", visitRequest.getDomainName(), smtpVisits);
        collectedData.put(smtpCrawler, smtpVisits);
      }


      return VisitResult.builder()
              .visitRequest(visitRequest)
              .dnsCrawlResult(dnsCrawlResult)
              .featuresList(featuresList)
              .vatCrawlResult(vatCrawlResult)
              .siteVisit(siteVisit)
              .collectedData(collectedData)
              .build();
    } finally {
      sample.stop(meterRegistry.timer("crawler.collectData"));
    }
  }

  // TODO: finish this idea
  @SuppressWarnings("unused")
  public void visit2(VisitRequest visitRequest) {
    logger.info("Starting visit for {}", visitRequest.getDomainName());
    Map<String, List<?>> dataPerModule = new HashMap<>();
    // GET THE DATA
    for (CrawlerModule<?> crawlerModule : crawlerModules) {
      List<?> data = crawlerModule.collectData(visitRequest);
      dataPerModule.put(crawlerModule.key(), data);
    }
    // SAVE TO DATABASE
    for (CrawlerModule<?> crawlerModule : crawlerModules) {
      List<?> data = dataPerModule.get(crawlerModule.key());
      crawlerModule.save(data);
    }
    // ADD TO CACHE etc
    logger.info("Saved results for visit to {}", visitRequest.getDomainName());
    for (CrawlerModule<?> crawlerModule : crawlerModules) {
      List<?> data = dataPerModule.get(crawlerModule.key());
      crawlerModule.afterSave(data);
    }
  }


}

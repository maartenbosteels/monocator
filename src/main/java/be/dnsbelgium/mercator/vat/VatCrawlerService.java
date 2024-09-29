package be.dnsbelgium.mercator.vat;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;
import be.dnsbelgium.mercator.vat.domain.VatScraper;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class VatCrawlerService {

  private static final Logger logger = getLogger(VatCrawlerService.class);

  private final VatScraper vatScraper;

  @Setter
  @Value("${vat.crawler.max.visits.per.domain:10}")
  private int maxVisitsPerDomain = 10;

  @Setter
  @Value("${vat.crawler.persist.page.visits:false}")
  private boolean persistPageVisits = false;

  @Setter
  @Value("${vat.crawler.persist.first.page.visit:false}")
  private boolean persistFirstPageVisit = false;

  @Setter
  @Value("${vat.crawler.persist.body.text:false}")
  private boolean persistBodyText = false;

  @Autowired
  public VatCrawlerService(VatScraper vatScraper) {
    this.vatScraper = vatScraper;
  }

  @PostConstruct
  public void init() {
    logger.info("maxVisitsPerDomain={}", maxVisitsPerDomain);
    logger.info("persistPageVisits={}", persistPageVisits);
    logger.info("persistBodyText={}", persistBodyText);
    logger.info("persistFirstPageVisit={}", persistFirstPageVisit);
  }

  public SiteVisit findVatValues(VisitRequest visitRequest) {
    String fqdn = visitRequest.getDomainName();
    logger.debug("Searching VAT info for domainName={} and visitId={}", fqdn, visitRequest.getVisitId());

    String startURL = "http://www." + fqdn;

    HttpUrl url = HttpUrl.parse(startURL);

    if (url == null) {
      // this is probably a bug: log + throw
      logger.error("VisitRequest {} => invalid URL: {} ", visitRequest, startURL);
      String message = String.format("visitRequest %s lead to invalid url: null", visitRequest);
      throw new RuntimeException(message);
    }

    SiteVisit siteVisit = vatScraper.visit(url, maxVisitsPerDomain);
    logger.debug("siteVisit = {}", siteVisit);

    logger.info("visitId={} domain={} vat={}", visitRequest.getVisitId(), visitRequest.getDomainName(), siteVisit.getVatValues());
    return siteVisit;
  }

  public VatCrawlResult convert(VisitRequest visitRequest, SiteVisit siteVisit) {
    String matchingUrl = (siteVisit.getMatchingURL() != null) ?  siteVisit.getMatchingURL().toString() : null;
    List<String> visited = siteVisit.getVisitedURLs()
            .stream().map(HttpUrl::toString).collect(Collectors.toList());
    VatCrawlResult crawlResult = VatCrawlResult.builder()
            .visitId(visitRequest.getVisitId())
            .domainName(visitRequest.getDomainName())
            .startUrl(siteVisit.getBaseURL().toString())
            .crawlStarted(siteVisit.getStarted())
            .crawlFinished(siteVisit.getFinished())
            .vatValues(siteVisit.getVatValues())
            .matchingUrl(matchingUrl)
            .visitedUrls(visited)
            .build();

    crawlResult.abbreviateData();
    return crawlResult;
  }

}

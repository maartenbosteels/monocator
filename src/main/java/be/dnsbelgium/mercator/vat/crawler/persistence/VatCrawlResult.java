package be.dnsbelgium.mercator.vat.crawler.persistence;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
public class VatCrawlResult {

  private String visitId;
  private String domainName;

  private String startUrl;
  private String matchingUrl;

  private Instant crawlStarted;
  private Instant crawlFinished;

  private List<String> vatValues;
  private List<String> visitedUrls;

  public void abbreviateData() {
    domainName = StringUtils.abbreviate(domainName, 255);
    startUrl = StringUtils.abbreviate(startUrl, 255);
    matchingUrl = StringUtils.abbreviate(matchingUrl, 255);
  }
}

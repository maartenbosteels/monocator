package be.dnsbelgium.mercator.tls.domain;

import java.util.List;

public record TlsCrawlResult (

  List<CrawlResult> crawlResults

) {}

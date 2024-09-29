package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;

import java.util.List;

public record VisitResult(
        VisitRequest visitRequest,
        DnsCrawlResult dnsCrawlResult,
        List<HtmlFeatures> featuresList,
        VatCrawlResult vatCrawlResult,
        SiteVisit siteVisit,
        SmtpVisit smtpVisit) {
}

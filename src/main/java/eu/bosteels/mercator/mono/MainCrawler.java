package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlResult;
import be.dnsbelgium.mercator.dns.domain.DnsCrawlService;
import be.dnsbelgium.mercator.feature.extraction.HtmlFeatureExtractor;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import be.dnsbelgium.mercator.smtp.SmtpCrawlService;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.tls.ports.TlsCrawler;
import be.dnsbelgium.mercator.vat.VatCrawlerService;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import be.dnsbelgium.mercator.vat.domain.Page;
import be.dnsbelgium.mercator.vat.domain.SiteVisit;
import eu.bosteels.mercator.mono.visits.VisitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SqlDialectInspection")
@Service
public class MainCrawler {

    private final DnsCrawlService dnsCrawlService;
    private final VatCrawlerService vatCrawlerService;
    private final SmtpCrawlService smtpCrawlService;
    private final TlsCrawler tlsCrawler;
    private final HtmlFeatureExtractor htmlFeatureExtractor;

    private final Repository repository;
    private final VisitService visitService;

    private final boolean smtpEnabled;

    private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);

    @Autowired
    public MainCrawler(DnsCrawlService dnsCrawlService,
                       VatCrawlerService vatCrawlerService,
                       SmtpCrawlService smtpCrawlService,
                       TlsCrawler tlsCrawler,
                       HtmlFeatureExtractor htmlFeatureExtractor,
                       Repository repository,
                       VisitService visitService
    ) {
        this.dnsCrawlService = dnsCrawlService;
        this.vatCrawlerService = vatCrawlerService;
        this.smtpCrawlService = smtpCrawlService;
        this.tlsCrawler = tlsCrawler;
        this.htmlFeatureExtractor = htmlFeatureExtractor;
        this.repository = repository;
        this.visitService = visitService;
        smtpEnabled = false;
    }

    public void visit(VisitRequest visitRequest) {
        VisitResult visitResult = collectData(visitRequest);
        visitService.save(visitResult);
        // TODO: now the full_scan rows have been saved => now add them to the fullScanCache
        repository.markDone(visitRequest);
    }

    private VisitResult collectData(VisitRequest visitRequest) {
        DnsCrawlResult dnsCrawlResult = dnsCrawlService.retrieveDnsRecords(visitRequest);
        SiteVisit siteVisit = vatCrawlerService.findVatValues(visitRequest);
        VatCrawlResult vatCrawlResult = vatCrawlerService.convert(visitRequest, siteVisit);
        logger.info("siteVisit = {}", siteVisit);
        List<HtmlFeatures> featuresList = new ArrayList<>();
        for (Page page : siteVisit.getVisitedPages().values()) {
            var html = page.getDocument().html();
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
        // disable TLS crawls until cache issue is fixed
        // tlsCrawler.process(visitRequest);
        if (smtpEnabled) {
            smtp(visitRequest);
        }
        return new VisitResult(
                visitRequest,
                dnsCrawlResult,
                featuresList,
                vatCrawlResult,
                siteVisit,
                null
        );
    }

    private void smtp(VisitRequest visitRequest) {
        SmtpVisit smtpVisit = smtpCrawlService.retrieveSmtpInfo(visitRequest);
        logger.info("smtpVisit = {}", smtpVisit);
    }

}

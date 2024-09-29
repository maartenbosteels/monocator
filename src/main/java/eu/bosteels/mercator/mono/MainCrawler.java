package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
    private final VisitRepository visitRepository;
    private final DuckDataSource dataSource;
    private final ThreadPoolTaskExecutor executor;
    private final Inserter inserter;

    private final boolean smtpEnabled;

    private static final Logger logger = LoggerFactory.getLogger(MainCrawler.class);

    @Autowired
    public MainCrawler(DnsCrawlService dnsCrawlService,
                       VatCrawlerService vatCrawlerService,
                       SmtpCrawlService smtpCrawlService,
                       TlsCrawler tlsCrawler,
                       HtmlFeatureExtractor htmlFeatureExtractor,
                       DuckDataSource dataSource,
                       Repository repository,
                       VisitRepository visitRepository,
                       @Qualifier("insertExecutor") ThreadPoolTaskExecutor executor,
                       Inserter inserter

    ) {
        this.dnsCrawlService = dnsCrawlService;
        this.vatCrawlerService = vatCrawlerService;
        this.smtpCrawlService = smtpCrawlService;
        this.tlsCrawler = tlsCrawler;
        this.htmlFeatureExtractor = htmlFeatureExtractor;
        this.dataSource = dataSource;
        this.repository = repository;
        this.visitRepository = visitRepository;
        this.executor = executor;
        this.inserter = inserter;
        smtpEnabled = false;
    }

    @PostConstruct
    public void init() {
        visitRepository.start();
    }


//    public void visit_old(VisitRequest visitRequest) {
//        // TODO: add metrics
//
//        VisitResult visitResult = collectData(visitRequest);
//
//        if (smtpEnabled) {
//            smtp(visitRequest);
//        }
//
//        // Now saving all collected data
//        visitDatabase.doInTransaction(jdbcTemplate -> {
//            Repository repository = new Repository(jdbcTemplate);
//            for (HtmlFeatures htmlFeatures : visitResult.featuresList()) {
//                repository.save(htmlFeatures);
//            }
//            repository.save(visitResult.dnsCrawlResult());
//            vatCrawlerService.save(repository, visitRequest, visitResult.siteVisit());
//            return null;
//        });
//        repository.markDone(visitRequest);
//        // TODO: now add items to fullScanCache
//    }


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
        // TODO: we should only add items to the fullScanCache when the inserts have been committed.
        // disable TLS crawls until this is fixed
        // tlsCrawler.process(visitRequest);
        return new VisitResult(
                visitRequest,
                dnsCrawlResult,
                featuresList,
                vatCrawlResult,
                siteVisit,
                null
        );
    }

    public void visit(VisitRequest visitRequest) {
        VisitResult visitResult = collectData(visitRequest);
        try {
            // Get read lock BEFORE starting a new transaction
            // There's a chance we have to wait until a database export is done.
            visitRepository.readWriteLock().writeLock().lock();
            logger.debug("saving with readLock");

            visitRepository.save(visitResult);
            // saveAsync(visitResult);
            inserter.queue(visitResult);

        } finally {
            visitRepository.readWriteLock().writeLock().unlock();
            logger.debug("released readLock");
        }
        // TODO: now the full_scan rows have been saved => now add them to the fullScanCache
        repository.markDone(visitRequest);
        visitRepository.afterCommit();
    }

    public void saveAsync(VisitResult visitResult) {
        executor.execute(() -> visitRepository.save(visitResult));
    }


    private void smtp(VisitRequest visitRequest) {
        // TODO: check SMTP crawler logic and re-enable
        SmtpVisit smtpVisit = smtpCrawlService.retrieveSmtpInfo(visitRequest);
        logger.info("smtpVisit = {}", smtpVisit);
    }

    //    public void visit(VisitRequest visitRequest) {
//        DnsCrawlResult dnsCrawlResult = dnsCrawlService.retrieveDnsRecords(visitRequest);
//        repository.save(dnsCrawlResult);
//
//        // this will already save the PageVisit objects and the VatCrawlResult
//        SiteVisit siteVisit = vatCrawlerService.findVatValues(visitRequest, true);
//        logger.info("siteVisit = {}", siteVisit);
//        logger.info("visited: {}", siteVisit.getVisitedURLs());
//
//        for (Page page : siteVisit.getVisitedPages().values()) {
//            var html = page.getDocument().html();
//            var features = htmlFeatureExtractor.extractFromHtml(
//                    html,
//                    page.getUrl().url().toExternalForm(),
//                    visitRequest.getDomainName()
//            );
//            features.visitId = visitRequest.getVisitId();
//            features.crawlTimestamp = ZonedDateTime.now();
//            features.domainName = visitRequest.getDomainName();
//
//            repository.save(features);
//        }
//        // TODO: we should only add items to the fullScanCache when are inserts have been committed.
//        // disable TLS crawls until this is fixed
//        // tlsCrawler.process(visitRequest);
//
//        if (smtpEnabled) {
//            smtp(visitRequest);
//        }
//
//        repository.markDone(visitRequest);
//    }

}

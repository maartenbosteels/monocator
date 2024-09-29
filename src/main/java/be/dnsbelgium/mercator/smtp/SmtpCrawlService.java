package be.dnsbelgium.mercator.smtp;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.smtp.domain.crawler.SmtpAnalyzer;
import be.dnsbelgium.mercator.smtp.metrics.MetricName;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.smtp.persistence.repositories.SmtpVisitRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class SmtpCrawlService {

  private static final Logger logger = getLogger(SmtpCrawlService.class);

  private final SmtpVisitRepository repository;

  private final SmtpAnalyzer analyzer;
  private final MeterRegistry meterRegistry;

  public SmtpCrawlService(SmtpVisitRepository repository,
                          SmtpAnalyzer analyzer, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.analyzer = analyzer;
    this.meterRegistry = meterRegistry;
  }


  public SmtpVisit retrieveSmtpInfo(VisitRequest visitRequest) {
    String fqdn = visitRequest.getDomainName();
    logger.debug("Retrieving SMTP info for domainName = {}", fqdn);
    SmtpVisit result = analyzer.visit(fqdn);
    TxLogger.log(getClass(), "retrieveSmtpInfo");
    result.setVisitId(visitRequest.getVisitId());
    return result;
  }

  @Transactional
  public Optional<SmtpVisit> find(String visitId) {
    Optional<SmtpVisit> smtpVisit = repository.findByVisitId(visitId);
    logger.debug("find by visitId => {}", smtpVisit);
    return smtpVisit;
  }

  @Transactional
  public void save(SmtpVisit visit) {
    logger.debug("About to save SmtpVisit for {}", visit.getDomainName());
    // Save the conversations
//    for (var host : visit.getHosts()) {
//      if (host.getConversation().getId() == null) {
//        var convo = conversationRepository.save(host.getConversation());
//        host.setConversation(convo);
//      }
//    }
    //repository.save(visit);
    meterRegistry.counter(MetricName.SMTP_RESULTS_SAVED).increment();
    logger.debug("Saved SmtpVisit for {} with visitId={}", visit.getDomainName(), visit.getVisitId());
  }

}

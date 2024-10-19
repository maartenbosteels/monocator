package be.dnsbelgium.mercator.smtp.ports;

import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.smtp.SmtpCrawlService;
import be.dnsbelgium.mercator.smtp.domain.crawler.SmtpConversationCache;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringJUnitConfig({SmtpCrawler.class, MetricsAutoConfiguration.class,
  CompositeMeterRegistryAutoConfiguration.class, SmtpConversationCache.class})
class SmtpCrawlerTest {

  @Autowired
  SmtpCrawler crawler;

  //@MockitoBean
  @MockBean
  SmtpCrawlService service;

  @Test
  public void nullRequestIsIgnored() throws Exception {
    crawler.process(null);
    verify(service, never()).retrieveSmtpInfo(any(VisitRequest.class));
  }

  @Test
  public void missingDomainNameIsIgnored() throws Exception {
    VisitRequest request = new VisitRequest(VisitIdGenerator.generate(), null);
    crawler.process(request);
    verify(service, never()).retrieveSmtpInfo(any(VisitRequest.class));
  }

  @Test
  public void missingVisitIdIsIgnored() throws Exception {
    VisitRequest request = new VisitRequest(null, "abc.be");
    crawler.process(request);
    verify(service, never()).retrieveSmtpInfo(any(VisitRequest.class));
  }

  @Test
  public void happyPath() throws Exception {
    VisitRequest request = new VisitRequest(VisitIdGenerator.generate(), "abc.be");
    when(service.retrieveSmtpInfo(request)).thenReturn(new SmtpVisit());
    crawler.process(request);
    verify(service, times(1)).retrieveSmtpInfo(request);
  }

  @Test
  public void oneFailure() {
    VisitRequest request = new VisitRequest(VisitIdGenerator.generate(), "abc.be");
    doThrow(new RuntimeException("first failure")).when(service).retrieveSmtpInfo(request);
    assertThrows(RuntimeException.class, () -> crawler.process(request));
    verify(service, times(1)).retrieveSmtpInfo(request);
  }

  @Test
  public void duplicate() throws Exception {
    var visitId = VisitIdGenerator.generate();
    VisitRequest request = new VisitRequest(visitId, "abc.be");
    var visit = new SmtpVisit();
    when(service.retrieveSmtpInfo(request)).thenReturn(visit);
    when(service.find(visitId))
      .thenReturn(Optional.empty())
      .thenReturn(Optional.of(visit));
    crawler.process(request);
    crawler.process(request);
    verify(service).retrieveSmtpInfo(any(VisitRequest.class));
  }

}

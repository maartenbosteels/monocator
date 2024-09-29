package be.dnsbelgium.mercator.smtp.domain;

import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpConversation;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpHost;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpHostRepositoryTest {

  private static final Logger logger = LoggerFactory.getLogger(SmtpHostRepositoryTest.class);

  @Test
  void saveHostTest(){
    SmtpConversation conversation = new SmtpConversation();
    conversation.setIp("4.5.6.7");
    conversation.setIpVersion(4);
    conversation.setCountry("Belgium");

    var visitId = SmtpVisit.generateVisitId();
    SmtpVisit visit = new SmtpVisit();
    SmtpHost host = new SmtpHost();
    host.setHostName("dns.be");
    host.setFromMx(true);
    host.setPriority(10);
    host.setConversation(conversation);
    visit.setVisitId(visitId);
    visit.setDomainName("dnsbelgium.be");
    visit.add(host);
//    SmtpHost savedHost = repository.save(host);
//    assertThat(savedHost.getHostName()).isEqualTo("dns.be");
//    assertThat(savedHost.isFromMx()).isTrue();
//    assertThat(savedHost.getPriority()).isEqualTo(10);
//    assertThat(savedHost.getConversation()).isEqualTo(conversation);
  }

  @Test
  void findAllByVisitIdTest() {
    logger.info("System.getProperty(\"java.version\") = {}", System.getProperty("java.version"));
    SmtpConversation conversation = new SmtpConversation();
    conversation.setIp("4.5.6.7");
    conversation.setIpVersion(4);
    conversation.setCountry("Belgium");

    var visitId = SmtpVisit.generateVisitId();
    SmtpVisit visit = new SmtpVisit();
    SmtpHost host = new SmtpHost();
    host.setHostName("dns.be");
    host.setFromMx(true);
    host.setPriority(10);
    host.setConversation(conversation);
    visit.setVisitId(visitId);
    visit.setDomainName("dnsbelgium.be");
    visit.add(host);
//    repository.save(host);
//    List<SmtpHost> hosts = repository.findByVisitVisitId(visitId);
//    assertThat(hosts.size()).isEqualTo(1);
  }

}

package be.dnsbelgium.mercator.smtp.domain;

import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpConversation;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpHost;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import com.github.f4b6a3.ulid.Ulid;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SmtpConversationRepositoryTest {

  private String generateVisitId() {
    return Ulid.fast().toString();
  }

  @Test
  void saveConversationTest(){
    SmtpConversation conversation = new SmtpConversation();
    ZonedDateTime timestamp = ZonedDateTime.now();
    conversation.setIp("1.2.3.4");
    conversation.setIpVersion(4);
    conversation.setTimestamp(timestamp);
    conversation.setBanner("Welcome");
    conversation.setAsn(2147483648L);
    conversation.setAsnOrganisation("AsnOrganisation");
    conversation.setStartTlsOk(true);
    conversation.setStartTlsReplyCode(10);
    conversation.setErrorMessage("[1.2.3.4] Timed out waiting for a response to [initial response]");
    conversation.setConnectOK(true);
    conversation.setConnectionTimeMs(567);
    conversation.setSoftware("MailSoftware");
    conversation.setSoftwareVersion("1.3");
    conversation.setCountry("Belgium");
    Set<String> extensions = new HashSet<>();
    extensions.add("Test");
    extensions.add("Ook een test");
    conversation.setSupportedExtensions(extensions);

    assertThat(conversation.getIp()).isEqualTo("1.2.3.4");
    assertThat(conversation.getIpVersion()).isEqualTo(4);
    assertThat(conversation.getTimestamp()).isEqualTo(timestamp);
    assertThat(conversation.getBanner()).isEqualTo("Welcome");
    assertThat(conversation.getAsn()).isEqualTo(2147483648L);
    assertThat(conversation.getAsnOrganisation()).isEqualTo("AsnOrganisation");
    assertThat(conversation.isStartTlsOk()).isTrue();
    assertThat(conversation.getStartTlsReplyCode()).isEqualTo(10);
    assertThat(conversation.getErrorMessage()).isEqualTo("[1.2.3.4] Timed out waiting for a response to [initial response]");
    assertThat(conversation.isConnectOK()).isTrue();
    assertThat(conversation.getConnectionTimeMs()).isEqualTo(567);
    assertThat(conversation.getSoftware()).isEqualTo("MailSoftware");
    assertThat(conversation.getSoftwareVersion()).isEqualTo("1.3");
    assertThat(conversation.getCountry()).isEqualTo("Belgium");
    assertThat(conversation.getSupportedExtensions()).isEqualTo(extensions);
  }

  @Test
  void findByHostId(){
    SmtpConversation conversation = new SmtpConversation();
    conversation.setIp("4.5.6.7");
    conversation.setIpVersion(4);
    conversation.setCountry("Belgium");

    var visitId = generateVisitId();
    SmtpVisit visit = new SmtpVisit();
    SmtpHost host = new SmtpHost();
    host.setHostName("dns.be");
    host.setFromMx(true);
    host.setPriority(10);
    host.setConversation(conversation);
    visit.setVisitId(visitId);
    visit.setDomainName("dnsbelgium.be");
    visit.add(host);
  }

}

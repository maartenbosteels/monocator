package be.dnsbelgium.mercator.smtp;

import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.smtp.domain.crawler.*;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpConversation;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpHost;
import be.dnsbelgium.mercator.smtp.persistence.entities.SmtpVisit;
import be.dnsbelgium.mercator.smtp.persistence.repositories.SmtpVisitRepository;
import com.github.f4b6a3.ulid.Ulid;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
  classes = {
          SmtpCrawlService.class,
          SmtpVisitRepository.class,
          SmtpConfig.class,
          SmtpAnalyzer.class,
          DefaultSmtpIpAnalyzer.class,
          NioSmtpConversationFactory.class
  },
  properties =
    {
      "smtp.crawler.input.queue.name=smtp.queue",
      "smtp.crawler.initial-response-time-out=2s",
      "smtp.crawler.read-time-out=2s"
    })
@ActiveProfiles({"test", "local"})
@Disabled
class SmtpCrawlServiceTest {

  @MockitoBean
  MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Autowired
  SmtpCrawlService service;
  @Autowired
  SmtpVisitRepository repository;

  // This test does not really need SQS but when we don't start localstack, we get a stacktrace in the logs
  //  (Unable to execute HTTP request: Connect to localhost:4576 [localhost/127.0.0.1, localhost/0:0:0:0:0:0:0:1] failed)
  // because starts listening on SQS in a background thread.
  // Our options
  // (a) ignore the warning + stacktrace
  // (b) find a way to not start listening on SQS
  // (c) start LocalStack from within this test
  //
  //  So far we have chosen for option (c) which adds 17 seconds to the execution time of this test.

  @BeforeAll
  static void init() throws IOException, InterruptedException {
  }

  private static final Logger logger = getLogger(SmtpCrawlServiceTest.class);

  private String generateVisitId() {
    return Ulid.fast().toString();
  }

  @Test
  @Transactional
  public void integrationTest() throws Exception {
    var visitId = generateVisitId();
    VisitRequest request = new VisitRequest(visitId, "dnsbelgium.be");
    var visit = service.retrieveSmtpInfo(request);
    service.save(visit);
    Optional<SmtpVisit> find = repository.findByVisitId(visitId);
    assertThat(find).isPresent();
    SmtpVisit found = find.get();
    logger.info("found = {}", found);
    assertThat(found).isNotNull();
    assertThat(found.getDomainName()).isEqualTo(request.getDomainName());
    assertThat(found.getVisitId()).isEqualTo(visitId);
  }

  @Test
  void saveTest(){
    SmtpConversation conversation1 = new SmtpConversation();
    conversation1.setIp("1.2.3.4");
    conversation1.setIpVersion(4);

    SmtpConversation conversation2 = new SmtpConversation();
    conversation2.setIp("5.6.7.8");
    conversation2.setIpVersion(4);

    String visitId = generateVisitId();
    var visit = new SmtpVisit();
    visit.setVisitId(visitId);
    visit.setDomainName("dnsbelgium.be");
    visit.setNumConversations(2);

    var host = new SmtpHost();
    host.setConversation(conversation1);
    host.setHostName("protection.outlook.com");
    host.setPriority(0);
    host.setFromMx(true);
    host.setVisit(visit);

    var host2 = new SmtpHost();
    host2.setConversation(conversation2);
    host2.setHostName("protection.outlook.com");
    host2.setPriority(0);
    host2.setFromMx(true);
    host2.setVisit(visit);

    List<SmtpHost> hosts = new ArrayList<>();
    hosts.add(host);
    hosts.add(host2);

    visit.setHosts(hosts);

    service.save(visit);

    Optional<SmtpVisit> savedVisit = repository.findByVisitId(visitId);
    assertThat(savedVisit).isPresent();
    SmtpVisit foundVisit = savedVisit.get();

    assertThat(foundVisit.getDomainName()).isEqualTo("dnsbelgium.be");

    logger.info("savedVisit.hosts.size = {}", savedVisit.get().getHosts().size());

    assertThat(foundVisit.getHosts().size()).isEqualTo(2);
    assertThat(foundVisit.getHosts().get(0).getHostName()).isEqualTo("protection.outlook.com");
    assertThat(foundVisit.getHosts().get(0).getConversation().getIp()).isEqualTo("1.2.3.4");
  }



}

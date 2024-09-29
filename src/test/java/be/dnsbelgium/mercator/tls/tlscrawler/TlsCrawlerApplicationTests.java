package be.dnsbelgium.mercator.tls.tlscrawler;

import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.common.VisitRequest;
import be.dnsbelgium.mercator.tls.ports.TlsCrawler;
import eu.bosteels.mercator.mono.MonocatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test"})
@ContextConfiguration(classes = MonocatorApplication.class)
class TlsCrawlerApplicationTests {

  @Autowired
  TlsCrawler tlsCrawler;

  @Test
  void contextLoads() {
  }

  @Test
  void process() {
    VisitRequest visitRequest = new VisitRequest(VisitIdGenerator.generate(), "google.be");
    tlsCrawler.process(visitRequest);
  }

}

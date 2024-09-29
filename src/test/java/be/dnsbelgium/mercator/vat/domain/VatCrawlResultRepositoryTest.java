package be.dnsbelgium.mercator.vat.domain;

import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.vat.crawler.persistence.VatCrawlResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"local", "test"})
public class VatCrawlResultRepositoryTest {

  private static final Logger logger = getLogger(VatCrawlResultRepositoryTest.class);

  @Test
  @Commit
  public void insert() {
    String visitId = VisitIdGenerator.generate();
    VatCrawlResult vatCrawlResult = VatCrawlResult.builder()
            .domainName("dnsbelgium.be")
            .visitId(visitId)
            .vatValues(List.of("BE-0466158640", "BE-0123455"))
            .crawlStarted(Instant.now())
            .crawlFinished(Instant.now().plusSeconds(123))
            .visitedUrls(List.of(
                "https://www.dnsbelgium.be/",
                "https://www.dnsbelgium.be/nl/over-dns-belgium",
                "https://www.dnsbelgium.be/nl/contact")
            ).build();

    //vatCrawlResultRepository.save(vatCrawlResult);
    logger.info("vatCrawlResult = {}", vatCrawlResult);

    //Optional<VatCrawlResult> found = vatCrawlResultRepository.findById(vatCrawlResult.getId());
    //logger.info("found = {}", found);


  }
}

package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.common.VisitIdGenerator;
import be.dnsbelgium.mercator.feature.extraction.persistence.HtmlFeatures;
import eu.bosteels.mercator.mono.persistence.VisitRepository;
import eu.bosteels.mercator.mono.visits.VisitResult;
import eu.bosteels.mercator.mono.visits.VisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

/**
 * Saving html_features in the duckdb database is usually fast (~10-30 ms)
 * But sometimes rather slow (800-900ms) in the goal of this test was to find out what is causing this slowness.
 * Unfortunately, I still don't know for sure what the cause is ...
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"local" })
@AutoConfigureObservability
@ContextConfiguration(classes = MonocatorApplication.class)
public class DatabasePerformanceTest {

  @Autowired
  VisitRepository visitRepository;

  @Autowired
  VisitService visitService;

  @Autowired
  PlatformTransactionManager transactionManager;

  private static final Logger logger = LoggerFactory.getLogger(DatabasePerformanceTest.class);

  @BeforeEach
  public void init() {
  }

  @Test
  @Transactional
  public void htmlFeatures() throws InterruptedException {
    visitRepository.attachAndUse();

    for (int k=0; k<20; k++) {

      Instant start = Instant.now();
      for (int i=0; i<5000; i++ ) {
        HtmlFeatures htmlFeatures = new HtmlFeatures();
        htmlFeatures.visitId = VisitIdGenerator.generate();
        htmlFeatures.domainName = "google.com";
        htmlFeatures.crawlTimestamp = ZonedDateTime.now();
        htmlFeatures.body_text = "hello world";
        htmlFeatures.external_hosts = List.of("google.com", "facebook.com");
        htmlFeatures.linkedin_links = List.of("linkedin.com/abc", "https://linkedin.com/xxx");
        visitRepository.save(htmlFeatures);
      }
      Instant done = Instant.now();
      Duration duration = Duration.between(start, done);
      logger.info("saving 5000 rows took {}", duration);
      logger.info("Sleeping 60s to give prometheus time to scrape us");
      Thread.sleep(60_000);

    }
  }

  private HtmlFeatures features() {
    HtmlFeatures htmlFeatures = new HtmlFeatures();
    htmlFeatures.visitId = VisitIdGenerator.generate();
    htmlFeatures.domainName = "google.com";
    htmlFeatures.html_length = 4578;
    htmlFeatures.crawlTimestamp = ZonedDateTime.now();
    htmlFeatures.external_hosts = List.of("google.com", "facebook.com", randomAlphabetic(15), randomAlphabetic(8));
    htmlFeatures.body_text_language = "nl";
    htmlFeatures.body_text_language_2 = "fr";
    htmlFeatures.body_text_truncated = false;
    htmlFeatures.distance_title_final_dn = 17;
    htmlFeatures.distance_title_initial_dn = 2;
    htmlFeatures.fraction_words_title_final_dn = 0.45452f;
    htmlFeatures.fraction_words_title_initial_dn = 0.477278f;
    htmlFeatures.longest_subsequence_title_final_dn = 10;
    htmlFeatures.longest_subsequence_title_initial_dn = 12;
    htmlFeatures.meta_text = randomAlphabetic(5000);
    htmlFeatures.meta_text_truncated = false;
    htmlFeatures.title = randomAlphabetic(52);
    htmlFeatures.htmlstruct = randomAlphabetic(480);
    Random random = new Random();
    htmlFeatures.nb_currency_names = random.nextInt(45);
    htmlFeatures.nb_imgs = random.nextInt(45);
    htmlFeatures.nb_meta_keyw = random.nextInt(45);
    htmlFeatures.nb_button = random.nextInt(45);
    htmlFeatures.nb_numerical_strings = random.nextInt(45);
    htmlFeatures.nb_distinct_hosts_in_urls = random.nextInt(45);
    htmlFeatures.url = "http://www." + randomAlphabetic(15) + ".com";

    htmlFeatures.crawlTimestamp = ZonedDateTime.now();
    htmlFeatures.body_text = "<1>hello world</h1>" + randomAlphabetic(3500);
    htmlFeatures.external_hosts = List.of("google.com", "facebook.com");
    htmlFeatures.linkedin_links = List.of("linkedin.com/abc", "https://linkedin.com/xxx");
    htmlFeatures.vimeo_links = List.of("google.com", "facebook.com", randomAlphabetic(35));
    htmlFeatures.youtube_links = List.of("google.com", "facebook.com", randomAlphabetic(35));
    return htmlFeatures;
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void multiThreaded() throws InterruptedException {
    int loops = 5;
    int threads = 100;
    int rows = 10_000;
    System.out.println("loop starting");
    for (int k=0; k<loops; k++) {
      logger.info("starting with loop {} out of {}", k+1, loops);
      ExecutorService executorService = Executors.newFixedThreadPool(threads);
      Instant start = Instant.now();
      for (int i=0; i<rows; i++ ) {
        executorService.submit(() -> {
          try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.execute(status -> {
              visitRepository.attachAndUse();
              visitRepository.save(features());
              return null;
            });
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
      }
      System.out.println("loop done");
      executorService.shutdown();
      executorService.awaitTermination(10, TimeUnit.MINUTES);
      Instant done = Instant.now();
      Duration duration = Duration.between(start, done);
      logger.info("saving {} rows with {} threads took {}", rows, threads, duration);
      logger.info("Sleeping 5s");
      Thread.sleep(5_000);

    }
    logger.info("Sleeping 60s to give prometheus time to scrape us");
    Thread.sleep(60_000);
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void multiThreaded_v2() throws InterruptedException {
    int loops = 5;
    int threads = 100;
    int rows = 10_000;
    System.out.println("loop starting");
    for (int k=0; k<loops; k++) {
      logger.info("starting with loop {} out of {}", k+1, loops);
      ExecutorService executorService = Executors.newFixedThreadPool(threads);
      Instant start = Instant.now();
      for (int i=0; i<rows; i++ ) {
        executorService.submit(() -> {
          try {
            List<HtmlFeatures> featuresList = List.of(features());
            VisitResult result = new VisitResult(
                    null, null, featuresList, null, null, null,null
            );
            visitService.save(result);
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
      }
      System.out.println("loop done");
      executorService.shutdown();
      executorService.awaitTermination(10, TimeUnit.MINUTES);
      Instant done = Instant.now();
      Duration duration = Duration.between(start, done);
      logger.info("saving {} rows with {} threads took {}", rows, threads, duration);
      logger.info("Sleeping 5s");
      Thread.sleep(5_000);

    }
    logger.info("Sleeping 60s to give prometheus time to scrape us");
    Thread.sleep(60_000);


  }

}
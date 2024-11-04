package eu.bosteels.mercator.mono.mvc;

import be.dnsbelgium.mercator.DuckDataSource;
import be.dnsbelgium.mercator.common.VisitRequest;
import eu.bosteels.mercator.mono.scheduling.WorkQueue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/")
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    private final MeterRegistry meterRegistry;

    private final WorkQueue workQueue;

    public HomeController(MeterRegistry meterRegistry, WorkQueue workQueue) {
      this.meterRegistry = meterRegistry;
      this.workQueue = workQueue;
    }

    @GetMapping
    public String index() {
        logger.info("index called");
        return "index";
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }

    @GetMapping("/test-htmx")
    public String test_htmx() {
        return "test-htmx";
    }


    @GetMapping("/hello_htmx")
    @ResponseBody
    public String hello_htmx(
        @RequestParam(name = "search", defaultValue = "") String search,
        @RequestParam(name = "millis", defaultValue = "100") int millis
    ) throws InterruptedException {
        System.out.println("hello_htmx called: search=[" + search + "] millis=[" + millis + "]");
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        Timer timer = meterRegistry.timer("mono.home", "search", search);
        timer.record(millis, TimeUnit.MILLISECONDS);
        logger.warn("timer = {}", timer);
        logger.warn("timer.totalTime = {} ms", timer.totalTime(TimeUnit.MILLISECONDS));
        logger.warn("timer.count     = {} ms", timer.count());

        workQueue.doSomething(millis);

      return (
          "<p>timer.totalTime = %s ms </p>").formatted(timer.totalTime(TimeUnit.MILLISECONDS)) +
          "<p> timer.count     = %d ms </p>".formatted(timer.count()) +
          "<p>hello, it is now" + now + " </p>";
    }
    @GetMapping("/submit_crawls")
    @ResponseBody
    public String submitCrawls(@RequestParam(name = "numberOfCrawls", defaultValue = "100") int numberOfCrawls) {
        logger.info("submitCrawls called: numberOfCrawls = {}", numberOfCrawls);
        var ds = DuckDataSource.memory();
        JdbcClient jdbcClient = JdbcClient.create(ds);
        List<String> names = jdbcClient
            .sql("select domain_name from 'tranco_be.parquet' limit ?")
            .param(numberOfCrawls)
            .query(String.class)
            .list();
        logger.info("names.size = {}", names.size());
        for (String domainName : names) {
            VisitRequest visitRequest = new VisitRequest(domainName);
            workQueue.add(visitRequest);
        }
        return "We added " + numberOfCrawls + " visit requests to the queue";
    }


    @GetMapping("/submit_crawl")
    public String submitCrawlForm(Model model) {
        logger.info("submitCrawlForm: model = {}", model);
        return "submit-crawl";
    }

    @PostMapping("/submit_crawl")
    public String submitCrawl(@RequestParam String domainName,
                              RedirectAttributes redirectAttributes) {
        logger.info("/submit_crawl called with domainName = {}", domainName);
        VisitRequest visitRequest = new VisitRequest(domainName);
        workQueue.add(visitRequest);
        redirectAttributes.addFlashAttribute("visitRequest", visitRequest);
        return "redirect:submit_crawl";
    }

}


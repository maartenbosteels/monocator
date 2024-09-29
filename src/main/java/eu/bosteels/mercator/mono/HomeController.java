package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.common.VisitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/")
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final WorkQueue workQueue;

    public HomeController(WorkQueue workQueue) {
        this.workQueue = workQueue;
    }

    @GetMapping
    public String index(Model model) {
        logger.error("index called, model = {}", model);
        return "index";
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(name = "search", defaultValue = "") String search) {
        System.out.println("hello called: " + search);
        return "hello";
    }


    @GetMapping("/hello_htmx")
    @ResponseBody
    public String hello_htmx(@RequestParam(name = "search", defaultValue = "") String search) {
        System.out.println("hello_htmx called: " + search);
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        return "<div>hello, it is now" + now + " </div>";
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


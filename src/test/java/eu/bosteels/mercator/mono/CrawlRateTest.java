package eu.bosteels.mercator.mono;



import org.junit.jupiter.api.Test;

import static java.time.ZonedDateTime.now;

public class CrawlRateTest {

    @Test
    public void print() {
        var rate = new CrawlRate(now(), 15*60*60, 15*60, 15);
        System.out.println("rate = " + rate);
    }

}
package be.dnsbelgium.mercator.smtp.domain.crawler.config;

import be.dnsbelgium.mercator.geoip.GeoIPService;
import eu.bosteels.mercator.mono.MonocatorApplication;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest(classes = { MonocatorApplication.class } )
@ActiveProfiles("test")

public class TestContext {

    @Autowired
    GeoIPService geoIPService;

    @Test
    public void enricher() {
        System.out.println("ok");
        System.out.println("geoIPService = " + geoIPService);
        assertNotNull(geoIPService);
        Optional<Pair<Long, String>> asn = geoIPService.lookupASN("8.8.8.8");
        if (asn.isPresent()) {
            System.out.println("asn     = " + asn.get().getKey());
            System.out.println("asn org = " + asn.get().getValue());
        }


    }
}

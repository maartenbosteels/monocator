package be.dnsbelgium.mercator.geoip;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class GeoIPServiceImplTest {

    @Test
    @Disabled
    public void init() {
        MaxMindConfig config = MaxMindConfig.free(
                Duration.ofDays(1),
                System.getenv("MAXMIND_LICENSE_KEY"),
                "~/maxmind/"
                );
        GeoIPServiceImpl geoIPService = new GeoIPServiceImpl(config);
        var asn  = geoIPService.lookupASN("8.8.8.8");
        System.out.println("asn = " + asn);
    }

}
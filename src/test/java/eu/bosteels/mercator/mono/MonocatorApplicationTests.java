package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.geoip.GeoIPService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

//@Import(TestcontainersConfiguration.class)
@SpringBootTest()
//properties ={ "duckdb.datasource.url=test.duckdb" }
class MonocatorApplicationTests {

  @Autowired
  GeoIPService geoIPService;

  @Test
  void contextLoads() {
    System.out.println("geoIPService = " + geoIPService);
    var asn = geoIPService.lookupASN("8.8.8.8");
    System.out.println("asn = " + asn);
  }

}

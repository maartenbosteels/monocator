package be.dnsbelgium.mercator.dns.domain;

import be.dnsbelgium.mercator.dns.dto.RecordType;
import be.dnsbelgium.mercator.dns.persistence.Request;
import be.dnsbelgium.mercator.dns.persistence.Response;
import be.dnsbelgium.mercator.dns.persistence.ResponseGeoIp;
import com.github.f4b6a3.ulid.Ulid;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

class RequestRepositoryTest {


  @Test
  void findByVisitId_with_responses_and_geoips() {
    var visitId = Ulid.fast().toString();
    Request request = Request.builder()
        .visitId(visitId)
        .domainName("dnsbelgium.be")
        .ok(true)
        .problem(null)
        .prefix("@")
        .recordType(RecordType.A)
        .rcode(0)
        .crawlTimestamp(ZonedDateTime.now())
        .build();

    // 1 Request has N Responses.
    Response r1 = Response.builder()
        .recordData("Some record data")
        .ttl(5000L)
        .build();
    Response r2 = Response.builder()
        .recordData("Some more record data")
        .ttl(5000L)
        .build();

    // Geo Ip for r1
    Pair<Long, String> asn = Pair.of(1L, "GROUP");
    ResponseGeoIp responseGeoIp = new ResponseGeoIp(asn, "BE", 4, "1.2.3.4");
    r1.getResponseGeoIps().add(responseGeoIp);

    request.setResponses(List.of(r1, r2));

    // TODO: re-enable asserts with new Repository implementation

    //requestRepository.save(request);
    //List<Request> requests = requestRepository.findByVisitId(visitId);

  //    assertThat(requests).hasSize(1);
  //    assertThat(requests.get(0).getResponses()).hasSize(2);
  //
  //    assertThat(requests.get(0).getResponses().get(0)).isEqualTo(r1);
  //    assertThat(requests.get(0).getResponses().get(1)).isEqualTo(r2);
  //
  //    assertThat(requests.get(0).getResponses().get(0).getResponseGeoIps()).hasSize(1);
  //    assertThat(requests.get(0).getResponses().get(0).getResponseGeoIps().get(0).getAsn()).isEqualTo(String.valueOf(asn.getLeft()));
  //    assertThat(requests.get(0).getResponses().get(0).getResponseGeoIps().get(0).getCountry()).isEqualTo("BE");
  //
  //    assertThat(requests.get(0).getResponses().get(1).getResponseGeoIps()).hasSize(0);

  }
}

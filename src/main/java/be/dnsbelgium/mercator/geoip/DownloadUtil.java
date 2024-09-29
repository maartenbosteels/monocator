package be.dnsbelgium.mercator.geoip;

import java.util.Optional;

import lombok.extern.log4j.Log4j2;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

@Log4j2
public class DownloadUtil {

  private DownloadUtil() {}

  public static Optional<byte[]> getAsBytes(String url, String logUrl, int timeoutInSeconds) {
      log.info("GET URL: {}", logUrl);
      log.info("GET URL: {}", url);

    Timeout timeout = Timeout.ofSeconds(timeoutInSeconds);
    try (CloseableHttpClient client =
                 HttpClientBuilder
                         .create()
                         .setDefaultRequestConfig(createConfig(timeout))
                         .build() )         {


      try(CloseableHttpResponse response = client.execute(new HttpGet(url))){

        if (response.getCode() == HttpStatus.SC_OK) {
          return Optional.ofNullable(EntityUtils.toByteArray(response.getEntity()));
        } else {
          log.error("GET error: {}", response.getCode());
          log.error("GET error: {}", response.getReasonPhrase());
        }
      }
    } catch (Exception e) {
        log.error("Error executing HTTP GET request for: {}", logUrl);
    }

    return Optional.empty();
  }

  private static RequestConfig createConfig(Timeout timeout) {
    return RequestConfig
            .custom()
            // timeout for waiting during creating of connection
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            // socket has timeout, for slow senders
            .setResponseTimeout(timeout)
            // do not let the apache http client initiate redirects
            // build it
            .build();
  }


}
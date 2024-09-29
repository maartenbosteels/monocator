package be.dnsbelgium.mercator.vat.domain;

import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.slf4j.LoggerFactory.getLogger;

@SuppressWarnings({"ConstantConditions", "HttpUrlsUsage"})
public class UrlTests {

  private static final Logger logger = getLogger(UrlTests.class);

  // See https://square.github.io/okhttp/4.x/okhttp/okhttp3/-http-url/#why-another-url-model
  // for why use HttpUrl instead of java.net.URL

  @Test
  public void javaURL() throws MalformedURLException, URISyntaxException {
    String attack = "http://example.com/static/images/../../../../../etc/passwd";
    System.out.println(new URL(attack).getPath());
    System.out.println(new URI(attack).getPath());
    System.out.println(HttpUrl.parse(attack).encodedPath());
    System.out.println(HttpUrl.parse(attack).pathSegments());
    System.out.println(HttpUrl.parse(attack).encodedPathSegments());
  }

  @Test
  public void equals() throws MalformedURLException {
    URL url1 = new URL("http://example.com");
    URL url2 = new URL("http://example.org");
    boolean equals = url1.equals(url2);
    logger.info("equals = {}", equals);

    HttpUrl httpUrl1 = HttpUrl.parse("http://example.com");
    HttpUrl httpUrl2 = HttpUrl.parse("http://example.org");
    logger.info("equals = {}", httpUrl1.equals(httpUrl2));

    httpUrl1 = HttpUrl.parse("http://example.com/abc/../def");
    httpUrl2 = HttpUrl.parse("http://example.com/def");
    logger.info("equals = {}", httpUrl1.equals(httpUrl2));

    url1 = new URL("http://example.com/abc/../def");
    url2 = new URL("http://example.org/def");
    logger.info("equals = {}", url1.equals(url2));

  }

}

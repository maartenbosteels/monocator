package be.dnsbelgium.mercator.smtp.persistence.entities;

import com.github.f4b6a3.ulid.Ulid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SmtpVisit {
  private String visitId;

  private String domainName;

  private ZonedDateTime timestamp = ZonedDateTime.now();

  private int numConversations = 0;

  @ToString.Exclude
  private List<SmtpHost> hosts = new ArrayList<>();

  private static final Logger logger = getLogger(SmtpVisit.class);

  private CrawlStatus crawlStatus;

  public SmtpVisit(String visitId, String domainName) {
    logger.debug("Creating new SmtpVisit with visitId={} and domainName={}", visitId, domainName);
    this.visitId = visitId;
    this.domainName = domainName;
  }

  public void add(SmtpHost host) {
    host.setVisit(this);
    hosts.add(host);
    ++numConversations;
  }

  public static String generateVisitId() {
    return Ulid.fast().toString();
  }

}

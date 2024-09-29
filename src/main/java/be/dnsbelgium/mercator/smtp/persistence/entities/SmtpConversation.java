package be.dnsbelgium.mercator.smtp.persistence.entities;

import be.dnsbelgium.mercator.smtp.dto.Error;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SmtpConversation {
  private Long id;

  private String ip;

  private Long asn;

  private String country;

  private String asnOrganisation;

  // Welcome message received from server
  private String banner;

  // True if connection was successful
  private boolean connectOK = false;

  // Reply code received after connect
  private int connectReplyCode;

  // extensions reported in response to EHLO
  public Set<String> supportedExtensions = new HashSet<>();

  // IP version (4 or 6)
  private int ipVersion;

  // True if STARTTLS is supported
  private boolean startTlsOk = false;

  // Reply code received after STARTTLS command
  private int startTlsReplyCode;

  // Error message from crawler, in case something went wrong
  private String errorMessage;

  private Error error;

  // Time (ms) it took to create a connection
  private long connectionTimeMs = -1;

  // SMTP software detected from banner (wild guess, easy to spoof)
  private String software;

  // SMTP software version detected from banner (wild guess, easy to spoof)
  private String softwareVersion;

  private ZonedDateTime timestamp = ZonedDateTime.now();

  //@Transient
  private final static String NULL_BYTE = "\u0000";
  //@Transient
  private final static String EMPTY_STRING = "";

  public SmtpConversation(InetAddress ip) {
    this.ip = ip.getHostAddress();
    if (ip instanceof Inet4Address) {
      ipVersion = 4;
    }
    if (ip instanceof Inet6Address) {
      ipVersion = 6;
    }
  }

  // Some servers return binary data and Postgres does not like it
  public void clean() {
    this.country = clean(country);
    this.softwareVersion = clean(softwareVersion);
    this.software = clean(software);
    this.banner = clean(banner);
    this.asnOrganisation = clean(asnOrganisation);
    this.errorMessage = clean(errorMessage);
    this.ip = clean(ip);
    this.supportedExtensions = supportedExtensions.stream().map(SmtpConversation::clean).collect(Collectors.toSet());
  }

  private static String clean(String input) {
    return StringUtils.replace(input, NULL_BYTE, EMPTY_STRING);
  }
}

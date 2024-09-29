package be.dnsbelgium.mercator.tls.crawler.persistence.entities;

import lombok.*;

import java.time.ZonedDateTime;

@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@Builder
@Getter
@ToString
public class CrawlResultEntity {

  @Setter
  private Long id;

  private String visitId;

  private String hostName;

  private String domainName;

  private ZonedDateTime crawlTimestamp;

  private FullScanEntity fullScanEntity;

  private CertificateEntity leafCertificateEntity;

  private boolean certificateExpired;

  private boolean certificateTooSoon;

  private boolean chainTrustedByJavaPlatform;

  private boolean hostNameMatchesCertificate;

}

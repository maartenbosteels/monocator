package be.dnsbelgium.mercator.tls.crawler.persistence.repositories;

import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CertificateRepository  {

  //@Query("select c from CertificateEntity c where c.sha256fingerprint = :fingerprint")
  public Optional<CertificateEntity> findBySha256fingerprint( String fingerprint) {
    return Optional.empty();
  }

//  @Query("select c from CertificateEntity c" +
//    " join CrawlResultEntity cr on c.sha256fingerprint = cr.leafCertificateEntity" +
//    " where cr.id = :crawlResultId")
  public Optional<CertificateEntity> findByCrawlResultId( Long crawlResultId) {
    return Optional.empty();
  }

  public CertificateEntity save(CertificateEntity certificate) {
     return certificate;
  }
}

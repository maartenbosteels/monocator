package be.dnsbelgium.mercator.tls.crawler.persistence.repositories;

import be.dnsbelgium.mercator.tls.crawler.persistence.entities.CertificateEntity;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

//@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(classes = CertificateRepository.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"local", "test"})
public class CertificateRepositoryTest {

  private static final Logger logger = getLogger(CertificateRepositoryTest.class);

  @Autowired
  private CertificateRepository certificateRepository;


  @Test
  void save() {
    CertificateEntity certificateEntity = CertificateEntity.builder().sha256fingerprint("12345").build();
    certificateRepository.save(certificateEntity);
    logger.info("certificateEntity = {}", certificateEntity);
  }

  @Test
  @Commit
  void saveAllAttributes() {
    CertificateEntity certificateEntity = CertificateEntity.builder()
        .sha256fingerprint("12345678")
        .issuer("I am the issuer")
        .subject("I am the subject")
        .notBefore(Instant.now().minus(5, ChronoUnit.DAYS))
        .notAfter(Instant.now().plus(5, ChronoUnit.DAYS))
        .publicKeyLength(2048)
        .publicKeySchema("SHA-256")
        .serialNumberHex("70:ed:8d:46:88:9d:90:7f:0d:e5:04:1e")
        .subjectAltNames(List.of("abc.be", "xyz.com"))
        .signatureHashAlgorithm("")
        .version(3)
        .signedBySha256(null)
        .build();
    certificateRepository.save(certificateEntity);
    logger.info("certificateEntity = {}", certificateEntity);

    Optional<CertificateEntity> found = certificateRepository.findBySha256fingerprint("12345678");
    //assertThat(found).isPresent();
  }


  }

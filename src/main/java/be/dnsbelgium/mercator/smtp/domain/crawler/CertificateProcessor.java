package be.dnsbelgium.mercator.smtp.domain.crawler;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class CertificateProcessor {

  private static final Logger logger = getLogger(CertificateProcessor.class);

  private final boolean printCertificates;

  public CertificateProcessor(@Value("${smtp.crawler.printCertificates:false}") boolean printCertificates) {
    this.printCertificates = printCertificates;
  }

  @SuppressWarnings("unused")
  public void process(SSLSession sslSession) {
    // TODO:  do something useful with the certificate info. For now, we just log some properties
    try {
      Certificate[] certs = sslSession.getPeerCertificates();
      logger.debug("The SSL peer presented {} certificates.", certs.length);
      int certNr = 0;
      for (Certificate cert : certs) {
        certNr++;
        if (printCertificates) {
          logger.info("==== info about certificate nr {} ======", certNr);
          process(cert);
          logger.info("=========================================");
        }
      }
    } catch (SSLPeerUnverifiedException e) {
      logger.warn("SSLPeerUnverifiedException: {}", e.getMessage());
    }
  }

  private void process(Certificate cert) {
    try {
      logger.debug(" cert = {}", cert.toString());
      logger.debug(" cert.type = {}", cert.getType());
      logger.debug(" cert.pubkey = [{}]", cert.getPublicKey().toString());
      logger.debug(" cert.pubkey.algo = [{}]", cert.getPublicKey().getAlgorithm());
      logger.debug(" cert.pubkey.format = [{}]", cert.getPublicKey().getFormat());

      logger.info("  cert.class = " + cert.getClass());

      if (cert instanceof X509Certificate x509Certificate) {
        logger.debug("   x509Certificate.getSigAlgName        = {}", x509Certificate.getSigAlgName());
        logger.debug("   x509Certificate.getSigAlgOID         = {}", x509Certificate.getSigAlgOID());
        logger.debug("   x509Certificate.getExtendedKeyUsage  = {}", x509Certificate.getExtendedKeyUsage());
        logger.debug("   x509Certificate.getIssuerX500Principal.getName  = {}", x509Certificate.getIssuerX500Principal().getName());
        logger.debug("   x509Certificate.getIssuerX500Principal.toString = {}", x509Certificate.getIssuerX500Principal().toString());
        logger.debug("   x509Certificate.getSubjectX500Principal.getName = {}", x509Certificate.getSubjectX500Principal().getName());

        logger.debug("   x509Certificate.getExtendedKeyUsage.getSubjectAlternativeNames = {}", x509Certificate.getSubjectAlternativeNames());

        X500Principal subjectDN = x509Certificate.getSubjectX500Principal();
        X500Principal issuerDN = x509Certificate.getIssuerX500Principal();

        if (subjectDN != null) {
          logger.debug("  subjectDN.name = {}", subjectDN.getName());
        }
        if (issuerDN != null) {
          logger.debug("  issuerDN.name = {}", issuerDN.getName());
        }
        logger.debug("   x509Certificate.getExtendedKeyUsage.getSubjectX500Principal = {}", x509Certificate.getSubjectX500Principal());
      }
    } catch (CertificateParsingException e) {
      logger.error("could not parse certificate: {}", e.getMessage());
    }
  }
}

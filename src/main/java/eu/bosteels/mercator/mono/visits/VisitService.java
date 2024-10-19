package eu.bosteels.mercator.mono.visits;

import eu.bosteels.mercator.mono.VisitRepository;
import eu.bosteels.mercator.mono.VisitResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class VisitService {

  private final ReadWriteLock readWriteLock;
  private final AtomicInteger transactionCount = new AtomicInteger(0);
  private final VisitRepository visitRepository;
  private static final Logger logger = LoggerFactory.getLogger(VisitService.class);

  @Value("${visits.max.transactions.per_db:10000}")
  int maxTransactionsPerDatabase;

  public VisitService(VisitRepository visitRepository) {
    this.visitRepository = visitRepository;
    this.readWriteLock = new ReentrantReadWriteLock();
  }

  @PostConstruct
  public void init() {
    visitRepository.init();
  }

  @PreDestroy
  public void close() {
    logger.info("close => exporting database");
    exportDatabase(false);
  }

  public void save(VisitResult visitResult) {
    int count = transactionCount.getAndIncrement();
    logger.debug("transactionCount: {}", count);
    if (count == maxTransactionsPerDatabase) {
      logger.info("current database had {} transactions => exporting and then starting new db", count);
      exportDatabase(true);
    }
    doSave(visitResult);
  }

  private void doSave(VisitResult visitResult) {
    readWriteLock.readLock().lock();
    try {
      visitRepository.save(visitResult);
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  private void exportDatabase(boolean attachNewDatabase) {
    var start = Instant.now();
    readWriteLock.writeLock().lock();
    try {
      var end = Instant.now();
      logger.info("We now have the write-lock after waiting {}", Duration.between(start, end));
      visitRepository.exportDatabase(attachNewDatabase);
      transactionCount.set(0);
    } finally {
      readWriteLock.writeLock().unlock();
      logger.info("We now have released the write-lock after {}", Duration.between(start, Instant.now()));
    }
  }


}

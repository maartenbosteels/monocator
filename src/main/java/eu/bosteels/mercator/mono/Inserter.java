package eu.bosteels.mercator.mono;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class Inserter {

  LinkedBlockingQueue<VisitResult> queue = new LinkedBlockingQueue<>();

  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final AtomicInteger inserts = new AtomicInteger(0);

  private static final Logger logger = LoggerFactory.getLogger(Inserter.class);
  private final VisitRepository visitRepository;

  public Inserter(VisitRepository visitRepository) {
    this.visitRepository = visitRepository;
    logger.info("visitRepository = {}", visitRepository);
  }

  public void queue(VisitResult visitResult) {
    queue.add(visitResult);
    logger.info("queueSize: {}", queue.size());
  }

  @PostConstruct
  public void start() {
    new Thread(this::inserterLoop).start();
  }

  @PreDestroy
  public void close() {
    stopped.set(true);
    logger.info("Inserter closed");
  }

  public void inserterLoop() {
    logger.warn("inserterLoop started");
    while (!stopped.get()) {
      try {
        VisitResult visitResult = queue.poll(5, TimeUnit.SECONDS);
        if (visitResult != null) {
          visitRepository.save(visitResult);
          int inserted = inserts.incrementAndGet();
          logger.info("Inserts done: {}", inserted);
        }

      } catch (InterruptedException e) {
        logger.warn("Insertion interrupted");
        Thread.currentThread().interrupt();
      } catch (Exception e) {
        logger.error("Insertion error", e);
      }
    }
    logger.warn("Insertion stopped");


  }

}

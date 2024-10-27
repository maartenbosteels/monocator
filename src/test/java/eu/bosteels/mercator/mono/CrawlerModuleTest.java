package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.common.VisitRequest;

import java.util.*;

class CrawlerModuleTest {

  public interface Crawler <T> {
    List<T> collectData(VisitRequest request);
    List<T> find(String visitId);
    void save(T t);
    void saveItem(Object t);
    void afterSave(Object t);
  }

  public void visit(List<Crawler<?>> crawlers, VisitRequest visitRequest) {

    Map<Crawler<?>, List<?>> dataPerCrawler = new HashMap<>();

    for (Crawler<?> crawler : crawlers) {
      List<?> data = crawler.collectData(visitRequest);
      dataPerCrawler.put(crawler, data);
    }

    for (Crawler<?> crawler : dataPerCrawler.keySet()) {
      List<?> data = dataPerCrawler.get(crawler);
      for (Object item : data) {
        crawler.saveItem(item);
      }
    }

    for (Crawler<?> crawler : dataPerCrawler.keySet()) {
      List<?> data = dataPerCrawler.get(crawler);
      for (Object item : data) {
        crawler.afterSave(item);
      }
    }

  }



}
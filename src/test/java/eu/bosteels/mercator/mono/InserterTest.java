package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import eu.bosteels.mercator.mono.persistence.Insert;
import eu.bosteels.mercator.mono.persistence.Inserter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static eu.bosteels.mercator.mono.persistence.Inserter.allElementsSameType;

class InserterTest {

  DuckDataSource db = DuckDataSource.memory();
  JdbcClient client = JdbcClient.create(db);

  @Test
  public void test() {
    var db = DuckDataSource.memory();
    var client = JdbcClient.create(db);
    client.sql("create table mytable(name varchar, degrees varchar[], nested varchar[][])")
          .update();
    int rowsInserted = new Inserter(client,"mytable")
            .columnValue("name", "Thomas")
            .columnValue("degrees", List.of("a", "b", "c"))
            .columnValue("nested",
                    List.of(List.of("aa", "bb"), List.of("cc")))
            .execute();
    System.out.println("rowsInserted = " + rowsInserted);

    client.sql("select * from mytable").query().listOfRows()
            .forEach(System.out::println);

    client.sql("select nested[1], nested[1][1] from mytable").query().listOfRows()
            .forEach(System.out::println);


    List<String> list = List.of("aa", "bb", "cc");
    List<List<?>> nestedList = new ArrayList<>();
    nestedList.add(list);
    nestedList.add(list);
    //nestedList.add(nestedList);
    System.out.println("nestedList = " + nestedList);


    new Inserter(client,"mytable")
            .columnValue("name", "Thomas")
            .columnValue("degrees", List.of("a", "b", "c"))
            .columnValue("nested", nestedList)
            .execute();

    new Inserter(client,"mytable")
            .columnValue("name", "Thomas")
            .columnValue("degrees", List.of("a", "b", "c"))
            .columnValue("nested", List.of(List.of(1, 2)))
            .execute();

    client.sql("select * from mytable").query().listOfRows()
            .forEach(System.out::println);

  }

  @Test
  public void sameType() {
    var ok = allElementsSameType(List.of(1L, 2, 3, 5L));
    System.out.println("ok = " + ok);
    System.out.println("ok = " + Integer.class.isAssignableFrom(Long.class));
    System.out.println("ok = " + Long.class.isAssignableFrom(Long.class));
    System.out.println("ok = " + Long.class.isAssignableFrom(Integer.class));
  }

  @Test
  @Disabled
  public void timing() {
    client.sql("create table mytable(name varchar, degrees varchar[], nested varchar[][])")
            .update();
    // 8s for 10k => 1.250 per second

    Instant start = Instant.now();
    Insert insert = new Insert(client,"mytable");
    for (int i = 0; i<10_000; i++) {
      insert(insert);
    }
    Duration duration = Duration.between(start, Instant.now());
    System.out.println("10k => duration = " + duration);
  }

  private void insert(Insert insert) {
    insert.start()
            .columnValue("name", "Thomas")
            .columnValue("degrees", List.of("a", "b", "c"))
            .columnValue("nested", List.of(List.of("aa", "bb", "cc")))
            .execute();
  }

}
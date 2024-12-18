package be.dnsbelgium.mercator;

import org.springframework.boot.SpringApplication;

public class TestMonocatorApplication {

  public static void main(String[] args) {
    SpringApplication
            .from(MonocatorApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
  }

}

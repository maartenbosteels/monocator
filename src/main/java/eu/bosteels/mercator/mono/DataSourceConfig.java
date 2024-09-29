package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${duckdb.datasource.url:jdbc:duckdb:monocator-test.duckdb}")
    private String url;

    @Bean
    public DuckDataSource duckDataSource() {
        logger.info("creating DuckDataSource using url=[{}]", url);
        DuckDataSource duckDataSource = new DuckDataSource();
        duckDataSource.setUrl(url);
        duckDataSource.init();
        return duckDataSource;
    }

    @Bean
    public JdbcTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }


    @Bean(name = "insertExecutor")
    public ThreadPoolTaskExecutor inserter() {
        ThreadPoolTaskExecutor inserter = new ThreadPoolTaskExecutor();
        inserter.setCorePoolSize(1);
        inserter.setMaxPoolSize(1);
        inserter.setQueueCapacity(100);
        return inserter;
    }

}

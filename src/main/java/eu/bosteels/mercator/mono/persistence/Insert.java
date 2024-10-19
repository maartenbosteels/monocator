package eu.bosteels.mercator.mono.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.Assert;

@SuppressWarnings("SqlDialectInspection")
public class Insert {

  private final JdbcClient jdbcClient;
  private final String tableName;

  public Insert(JdbcClient jdbcClient, String tableName) {
    this.jdbcClient = jdbcClient;
    this.tableName = tableName;
    int tableCount = jdbcClient
            .sql("select count(1) from duckdb_tables() where table_name = ?")
            .param(tableName)
            .query(Integer.class)
            .single();
    Assert.isTrue(tableCount > 0, "Table \"" + tableName + "\" does not exist.");
  }

  public Inserter start() {
    return new Inserter(jdbcClient, tableName);
  }

}

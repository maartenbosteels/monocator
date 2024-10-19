package eu.bosteels.mercator.mono.persistence;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static eu.bosteels.mercator.mono.Repository.timestamp;

@SuppressWarnings("SqlDialectInspection")
public class Inserter {

  private final JdbcClient jdbcClient;

  private static final Logger logger = LoggerFactory.getLogger(Inserter.class);

  private final List<Object> expandedParameters = new ArrayList<>();

  private final StringBuilder insert = new StringBuilder();
  private final StringBuilder placeholders = new StringBuilder();

  public Inserter(JdbcClient jdbcClient, String tableName) {
    this.jdbcClient = jdbcClient;
    insert.append("INSERT INTO ").append(tableName).append(" (");
    placeholders.append(" values(");
  }

  public Inserter columnValue(String columnName, Object value) {
    insert.append(columnName);
    if (value instanceof List<?> list) {
      addValues(list);
    } else {
      if (value instanceof Instant instant) {
        Timestamp timestamp = timestamp(instant);
        expandedParameters.add(timestamp);
      } else {
        expandedParameters.add(value);
      }
      placeholders.append("?");
    }
    insert.append(",");
    placeholders.append(",");
    return this;
  }

  private void addValues(List<?> values) {
    placeholders.append("[");
    for (Object value : values) {
      if (value instanceof List<?> list) {
        addValues(list);
      } else {
        expandedParameters.add(value);
        placeholders.append("?");
      }
      placeholders.append(",");
    }
    placeholders.append("]");
  }

  public int execute() {
    String stmt =
            StringUtils.removeEnd(insert.toString(), ",") + ")" +
                    StringUtils.removeEnd(placeholders.toString(), ",") + ")";
    logger.info("{}, expandedParameters={}", stmt, expandedParameters);
    return jdbcClient
            .sql(stmt)
            .params(expandedParameters)
            .update();
  }

  public static boolean allElementsSameType(List<?> list) {
    if (list.isEmpty()) {
      return true;
    }
    Class<?> firstType = list.get(0).getClass();
    System.out.println("firstType = " + firstType);
    for (Object element : list) {
      if (!firstType.isInstance(element)) {
        return false;
      }
    }
    return true;
  }

}



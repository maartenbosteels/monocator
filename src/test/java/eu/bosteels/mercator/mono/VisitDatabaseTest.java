package eu.bosteels.mercator.mono;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitDatabaseTest {

//    @TempDir
//    File tempDir;

    // use this location if you want to manually inspect the results
    File tempDir = new File(System.getProperty("user.home") + "/test_mono/");

    private static final Logger logger = LoggerFactory.getLogger(VisitDatabaseTest.class);

    @Test
    public void constructor() throws IOException {
        VisitDatabase db = new VisitDatabase(
                new File(tempDir, "test.duck.db"),
                new File(tempDir, "test.exports"),
                5, true
        );
        logger.info("db = {}", db);
        db.init();
    }

    @Test
    public void queries() throws IOException {
        VisitDatabase db = new VisitDatabase(
                new File(tempDir, "test.duck.db"),
                new File(tempDir, "test.exports"),
                5, true
        );
        logger.info("db = {}", db);
        db.init();
        db.doInTransaction(jdbcTemplate -> {
            var list = jdbcTemplate.queryForList("show all tables");
            logger.info("list = {}", list);
            var settings = jdbcTemplate.queryForList("SELECT * FROM duckdb_settings()");
            logger.info("settings = {}", settings);
            return settings;
        });
    }

    @Test
    public void secondDbAfterExport() throws IOException {
        VisitDatabase db = new VisitDatabase(
                new File(tempDir, "test.duck.db"),
                new File(tempDir, "test.exports"),
                2, true
        );
        logger.info("db = {}", db);
        db.init();
        List<Map<String, Object>> tables =  db.doInTransaction(jdbcTemplate -> {
            jdbcTemplate.execute("create table tx1(id int)");
            return jdbcTemplate.queryForList("show tables");
        });
        logger.info("tables = {}", tables);
        assertThat(tables)
                .contains(Map.of("name", "tx1"));

        tables = db.doInTransaction(jdbcTemplate -> {
            jdbcTemplate.execute("create table tx2(id int)");
            return jdbcTemplate.queryForList("show tables");
        });
        logger.info("tables = {}", tables);
        assertThat(tables)
                .contains(Map.of("name", "tx1"))
                .contains(Map.of("name", "tx2"));

        tables = db.doInTransaction(jdbcTemplate -> {
            jdbcTemplate.execute("create table tx3(id int)");
            return jdbcTemplate.queryForList("show tables");
        });
        logger.info("tables = {}", tables);
        assertThat(tables)
                .doesNotContain(Map.of("name", "tx1"))
                .contains(Map.of("name", "tx3"));

        tables = db.doInTransaction(jdbcTemplate -> {
            jdbcTemplate.execute("create table tx4(id int)");
            return jdbcTemplate.queryForList("show tables");
        });
        logger.info("tables = {}", tables);
        assertThat(tables)
                 .contains(Map.of("name", "tx3"))
                 .contains(Map.of("name", "tx4"));
        db.close();
    }

}
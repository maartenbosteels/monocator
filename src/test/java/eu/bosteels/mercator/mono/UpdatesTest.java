package eu.bosteels.mercator.mono;

import be.dnsbelgium.mercator.DuckDataSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertThrows;


@SuppressWarnings("SqlDialectInspection")
@SpringBootTest
public class UpdatesTest {

    @Autowired
    DuckDataSource dataSource;

    @Test
    @Disabled // takes 5s to simply verify duckdb behaviour
    // transaction that commits first will succeed
    // other transations will fail with
    // TransactionContext Error: Failed to commit: PRIMARY KEY or UNIQUE constraint violated: duplicate key "1"

    public void concurentUpdates() throws ExecutionException, InterruptedException {
        System.out.println("starting ...");
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                "create or replace table test_locks(id int primary key, thread varchar)");
        var threadPool = Executors.newFixedThreadPool(2);
        ReentrantLock lock1 = new ReentrantLock();
        ReentrantLock lock2 = new ReentrantLock();
        var f1 = threadPool.submit(() -> {
            try {
                lock1.lock();
                var c = dataSource.getConnection();
                //jdbcTemplate.a
                c.setAutoCommit(false);
                var s = c.prepareStatement("insert into test_locks(id, thread) values(1, 't1')");
                s.execute();
                //jdbcTemplate.update("insert into test_locks(id, thread) values(1, 't1')");
                System.out.println("t1 has inserted");
                lock1.unlock();
                Thread.sleep(5000);
                lock2.lock();
                System.out.println("t1 about to commit, this will fail");
                s.execute("commit");
                System.out.println("t1 commit done");

            } catch (SQLException | InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

        });

        var f2 = threadPool.submit(() -> {
            try {
                var c = dataSource.getConnection();
                c.setAutoCommit(false);
                lock1.lock();
                lock2.lock();
                var s = c.prepareStatement("insert into test_locks(id, thread) values(1, 't2')");
                s.execute();
                System.out.println("t2 has inserted");
                lock1.unlock();
                lock2.unlock();
                System.out.println("t2 about to commit");
                s.execute("commit");
                System.out.println("t2 commit done");

            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        f2.get();
        assertThrows(ExecutionException.class, f1::get);
    }

}

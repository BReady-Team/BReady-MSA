package com.bready.server.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// 동시성 테스트가 요청들을 락 대기열에 원하는 순서로 세웠는지 확인
public final class MySqlLockWaits {

    private MySqlLockWaits() {}

    public static void awaitWaiters(MySQLContainer<?> mysql, int expected, Duration timeout) {
        await().atMost(timeout)
                .untilAsserted(() -> assertThat(countWaiters(mysql)).isEqualTo(expected));
    }

    private static int countWaiters(MySQLContainer<?> mysql) throws Exception {
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select count(distinct requesting_engine_transaction_id)"
                        + " from performance_schema.data_lock_waits")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}

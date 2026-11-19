
package com.revo.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldConnectToMySql() throws Exception {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT DATABASE()"
             )) {

            assertTrue(connection.isValid(2));

            assertEquals(
                    "MySQL",
                    connection.getMetaData().getDatabaseProductName()
            );

            assertTrue(resultSet.next());
            assertNotNull(resultSet.getString(1));
        }
    }
}

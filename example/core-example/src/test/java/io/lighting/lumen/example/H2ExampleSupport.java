package io.lighting.lumen.example;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

final class H2ExampleSupport {
    private H2ExampleSupport() {
    }

    static DataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + "_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    static void createOrdersTable(Db db) throws SQLException {
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, ORDER_NO VARCHAR(64), STATUS VARCHAR(32), TOTAL DECIMAL(10,2))",
            List.of()
        )));
    }

    static void insertOrder(Db db, long id, String orderNo, String status, int total) throws SQLException {
        db.execute(Command.of(new RenderedSql(
            "INSERT INTO ORDERS(ID, ORDER_NO, STATUS, TOTAL) VALUES (?, ?, ?, ?)",
            List.of(
                new Bind.Value(id, 0),
                new Bind.Value(orderNo, 0),
                new Bind.Value(status, 0),
                new Bind.Value(total, 0)
            )
        )));
    }
}

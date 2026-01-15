package io.lighting.lumen.dsl;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DbDslTest {
    @Test
    void selectsWithFluentDsl() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(dataSource())
            .build();

        Db db = lumen.db();
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE ORDERS (ID BIGINT PRIMARY KEY, ORDER_NO VARCHAR(64), STATUS VARCHAR(32))",
            List.of()
        )));
        db.execute(Command.of(new RenderedSql(
            "INSERT INTO ORDERS (ID, ORDER_NO, STATUS) VALUES (?, ?, ?)",
            List.of(
                new Bind.Value(1L, 0),
                new Bind.Value("NO-1", 0),
                new Bind.Value("NEW", 0)
            )
        )));
        db.execute(Command.of(new RenderedSql(
            "INSERT INTO ORDERS (ID, ORDER_NO, STATUS) VALUES (?, ?, ?)",
            List.of(
                new Bind.Value(2L, 0),
                new Bind.Value("NO-2", 0),
                new Bind.Value("PAID", 0)
            )
        )));

        List<OrderRow> rows = db.dsl()
            .select(OrderRow.class, OrderEntity::getId, OrderEntity::getOrderNo, OrderEntity::getStatus)
            .from(OrderEntity.class)
            .where()
            .equals(OrderEntity::getStatus, "NEW")
            .and()
            .like(OrderEntity::getOrderNo, "NO-%")
            .toList();

        assertEquals(1, rows.size());
        assertEquals("NO-1", rows.get(0).orderNo());
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lumen_fluent;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    @Table(name = "ORDERS")
    private static final class OrderEntity {
        @Column(name = "ID")
        private Long id;

        @Column(name = "ORDER_NO")
        private String orderNo;

        @Column(name = "STATUS")
        private String status;

        public Long getId() {
            return id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public String getStatus() {
            return status;
        }
    }

    private record OrderRow(Long id, String orderNo, String status) {
    }
}

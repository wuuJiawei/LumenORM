package io.lighting.lumen.dsl;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
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

    @Test
    void pagesWithFluentDsl() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(dataSource())
            .build();

        Db db = lumen.db();
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE P_ORDERS (ID BIGINT PRIMARY KEY, ORDER_NO VARCHAR(64), STATUS VARCHAR(32))",
            List.of()
        )));
        db.execute(Command.of(new RenderedSql(
            "INSERT INTO P_ORDERS (ID, ORDER_NO, STATUS) VALUES (?, ?, ?)",
            List.of(
                new Bind.Value(1L, 0),
                new Bind.Value("NO-1", 0),
                new Bind.Value("NEW", 0)
            )
        )));
        db.execute(Command.of(new RenderedSql(
            "INSERT INTO P_ORDERS (ID, ORDER_NO, STATUS) VALUES (?, ?, ?)",
            List.of(
                new Bind.Value(2L, 0),
                new Bind.Value("NO-2", 0),
                new Bind.Value("NEW", 0)
            )
        )));

        PageRequest request = PageRequest.of(1, 1);
        PageResult<OrderRow> page = db.dsl()
            .select(OrderRow.class, POrderEntity::getId, POrderEntity::getOrderNo, POrderEntity::getStatus)
            .from(POrderEntity.class)
            .where()
            .equals(POrderEntity::getStatus, "NEW")
            .toPage(request);

        assertEquals(1, page.items().size());
        assertEquals(2L, page.total());

        PageResult<OrderRow> noCount = db.dsl()
            .select(OrderRow.class, POrderEntity::getId, POrderEntity::getOrderNo, POrderEntity::getStatus)
            .from(POrderEntity.class)
            .where()
            .equals(POrderEntity::getStatus, "NEW")
            .toPage(request.withoutCount());

        assertEquals(1, noCount.items().size());
        assertEquals(PageResult.TOTAL_UNKNOWN, noCount.total());
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
        @Id(strategy = IdStrategy.AUTO)
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

    @Table(name = "P_ORDERS")
    private static final class POrderEntity {
        @Id(strategy = IdStrategy.AUTO)
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
}

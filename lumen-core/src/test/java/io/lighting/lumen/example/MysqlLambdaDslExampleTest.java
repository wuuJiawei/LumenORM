package io.lighting.lumen.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MysqlLambdaDslExampleTest {
    private static final String MYSQL_URL =
        "jdbc:mysql://localhost:3307/lumen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_USER = "lumen";
    private static final String MYSQL_PASSWORD = "lumen";
    private static final Dialect DIALECT = new LimitOffsetDialect("mysql", "`");

    @Test
    void runsDslWithLambdaColumns() throws SQLException {
        DefaultDb db = createDb();
        Dsl dsl = new Dsl(metaRegistry());
        io.lighting.lumen.dsl.Table baseOrders = dsl.table(Order.class);
        io.lighting.lumen.dsl.Table orders = baseOrders.as("o");
        SqlRenderer renderer = new SqlRenderer(DIALECT);

        InsertStmt insert = dsl.insertInto(baseOrders)
            .columns(Order::status, Order::total)
            .row("NEW", 55)
            .build();
        RenderedSql insertSql = renderer.render(insert, Bindings.empty());
        Long id = db.executeAndReturnGeneratedKey(Command.of(insertSql), "id", rs -> rs.getLong(1));

        UpdateStmt update = dsl.update(orders)
            .set(Order::status, "PAID")
            .where(orders.col(Order::id).eq(Dsl.param("id")))
            .build();
        RenderedSql updateSql = renderer.render(update, Bindings.of("id", id));
        int updated = db.executeOptimistic(Command.of(updateSql));
        assertEquals(1, updated);

        SelectStmt select = dsl.select(
                orders.col(Order::id).select(),
                orders.col(Order::status).select(),
                orders.col(Order::total).select()
            )
            .from(orders)
            .where(orders.col(Order::id).eq(Dsl.param("id")))
            .build();
        List<OrderView> rows = db.fetch(Query.of(select, Bindings.of("id", id)), OrderView.class);
        assertEquals(List.of(new OrderView(id, "PAID", 55)), rows);

        DeleteStmt delete = dsl.deleteFrom(orders)
            .where(orders.col(Order::id).eq(Dsl.param("id")))
            .build();
        RenderedSql deleteSql = renderer.render(delete, Bindings.of("id", id));
        int deleted = db.execute(Command.of(deleteSql));
        assertEquals(1, deleted);
    }

    private DefaultDb createDb() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL_URL);
        dataSource.setUser(MYSQL_USER);
        dataSource.setPassword(MYSQL_PASSWORD);
        EntityMetaRegistry metaRegistry = metaRegistry();
        EntityNameResolver resolver = EntityNameResolvers.from(Map.of("Order", Order.class));
        SqlRenderer renderer = new SqlRenderer(DIALECT);
        return new DefaultDb(
            new io.lighting.lumen.jdbc.JdbcExecutor(dataSource),
            renderer,
            DIALECT,
            metaRegistry,
            resolver
        );
    }

    private EntityMetaRegistry metaRegistry() {
        return new TestEntityMetaRegistry();
    }

    @Table(name = "orders")
    private static final class Order {
        @Id
        @Column(name = "id")
        private Long id;

        @Column(name = "status")
        private String status;

        @Column(name = "total")
        private int total;

        public Long id() {
            return id;
        }

        public String status() {
            return status;
        }

        public int total() {
            return total;
        }
    }

    private record OrderView(long id, String status, int total) {
    }
}

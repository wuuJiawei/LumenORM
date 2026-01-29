package io.lighting.lumen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Integration tests requiring Docker containers.
 * Run with: docker compose up -d
 * Then: mvn test -Dtest=DockerComposeIntegrationTest
 */
@Disabled("Requires Docker containers. Run 'docker compose up -d' first, then re-enable.")
class DockerComposeIntegrationTest {
    private static final DbConfig MYSQL = new DbConfig(
        "jdbc:mysql://localhost:3307/lumen",
        "root",
        "root",
        "id"
    );
    private static final DbConfig POSTGRES = new DbConfig(
        "jdbc:postgresql://localhost:5432/lumen",
        "lumen",
        "lumen",
        "id"
    );
    private static final DbConfig ORACLE = new DbConfig(
        "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
        "lumen",
        "lumen",
        "ID"
    );

    @Test
    void runsAgainstMySql() throws SQLException {
        runIntegration(MYSQL);
    }

    @Test
    void runsAgainstPostgres() throws SQLException {
        runIntegration(POSTGRES);
    }

    @Test
    void runsAgainstOracle() throws SQLException {
        runIntegration(ORACLE);
    }

    private void runIntegration(DbConfig config) throws SQLException {
        try (Connection connection = DriverManager.getConnection(config.url(), config.user(), config.password())) {
            DefaultDb db = createDb(connection);
            runTemplateScenario(db);
            runDmlScenario(db, config.generatedKeyColumn());
        }
    }

    private void runTemplateScenario(DefaultDb db) throws SQLException {
        String template = """
            SELECT o.id, o.status
            FROM @table(Order) o
            @where {
              @if(status != null) { o.status = :status }
              @if(ids) { AND o.id IN @in(:ids) }
            }
            @orderBy(:sort, allowed = { ID_ASC : o.id ASC, ID_DESC : o.id DESC }, default = ID_ASC)
            """;
        Bindings bindings = Bindings.of(
            "status", "NEW",
            "ids", List.of(1L, 3L),
            "sort", "ID_ASC"
        );

        List<OrderRow> rows = db.run(template, bindings, rs -> new OrderRow(rs.getLong(1), rs.getString(2)));

        assertEquals(List.of(new OrderRow(1L, "NEW"), new OrderRow(3L, "NEW")), rows);
    }

    private void runDmlScenario(DefaultDb db, String generatedKeyColumn) throws SQLException {
        Dsl dsl = new Dsl(new TestEntityMetaRegistry());
        io.lighting.lumen.dsl.Table orders = dsl.table(Order.class);
        SqlRenderer renderer = new SqlRenderer(new NoQuoteDialect());

        InsertStmt insert = dsl.insertInto(orders)
            .columns(orders.col("status"), orders.col("total"))
            .row("NEW", 42)
            .build();
        RenderedSql insertSql = renderer.render(insert, Bindings.empty());
        Long id = db.executeAndReturnGeneratedKey(Command.of(insertSql), generatedKeyColumn, rs -> rs.getLong(1));

        UpdateStmt update = dsl.update(orders)
            .set(orders.col("status"), "PAID")
            .where(orders.col("id").eq(Dsl.param("id")))
            .build();
        RenderedSql updateSql = renderer.render(update, Bindings.of("id", id));
        int updated = db.executeOptimistic(Command.of(updateSql));
        assertEquals(1, updated);

        SelectStmt select = dsl.select(orders.col("status").expr())
            .from(orders)
            .where(orders.col("id").eq(Dsl.param("id")))
            .build();
        List<String> statuses = db.fetch(Query.of(select, Bindings.of("id", id)), rs -> rs.getString(1));
        assertEquals(List.of("PAID"), statuses);

        DeleteStmt delete = dsl.deleteFrom(orders)
            .where(orders.col("id").eq(Dsl.param("id")))
            .build();
        RenderedSql deleteSql = renderer.render(delete, Bindings.of("id", id));
        int deleted = db.execute(Command.of(deleteSql));
        assertEquals(1, deleted);
    }

    private DefaultDb createDb(Connection connection) {
        EntityMetaRegistry metaRegistry = new TestEntityMetaRegistry();
        EntityNameResolver resolver = EntityNameResolvers.from(Map.of("Order", Order.class));
        Dialect dialect = new NoQuoteDialect();
        SqlRenderer renderer = new SqlRenderer(dialect);
        return new DefaultDb(
            new io.lighting.lumen.jdbc.JdbcExecutor(connection),
            renderer,
            dialect,
            metaRegistry,
            resolver
        );
    }

    private record DbConfig(String url, String user, String password, String generatedKeyColumn) {
    }

    private static final class NoQuoteDialect implements Dialect {
        @Override
        public String id() {
            return "plain";
        }

        @Override
        public String quoteIdent(String ident) {
            return ident;
        }

        @Override
        public RenderedPagination renderPagination(int page, int pageSize, List<io.lighting.lumen.sql.ast.OrderItem> orderBy) {
            throw new UnsupportedOperationException("Pagination not supported in docker integration tests");
        }
    }

    @Table(name = "orders")
    private static final class Order {
        @Id
        private Long id;

        @Column(name = "status")
        private String status;

        @Column(name = "total")
        private int total;
    }

    private record OrderRow(long id, String status) {
    }
}

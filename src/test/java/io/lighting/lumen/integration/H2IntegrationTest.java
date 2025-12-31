package io.lighting.lumen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class H2IntegrationTest {

    @Test
    void runsTemplateAgainstH2() throws SQLException {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        seedOrders(dataSource);
        DefaultDb db = createDb(dataSource);

        String template = """
            SELECT o.id, o.status
            FROM @table(Order) o
            @where {
              @if(status != null) { o.status = :status }
              @if(ids) { AND o.id IN @in(:ids) }
            }
            @orderBy(:sort, allowed = { ID_ASC : o.id ASC, ID_DESC : o.id DESC }, default = ID_ASC)
            @page(:page, :pageSize)
            """;
        Bindings bindings = Bindings.of(
            "status", "NEW",
            "ids", List.of(1L, 3L),
            "sort", "ID_ASC",
            "page", 1,
            "pageSize", 10
        );

        List<OrderRow> rows = db.run(template, bindings, rs -> new OrderRow(rs.getLong(1), rs.getString(2)));

        assertEquals(List.of(new OrderRow(1L, "NEW"), new OrderRow(3L, "NEW")), rows);
    }

    @Test
    void executesDmlAgainstH2() throws SQLException {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        DefaultDb db = createDb(dataSource);
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        Dsl dsl = new Dsl(new ReflectionEntityMetaRegistry());
        io.lighting.lumen.dsl.Table orders = dsl.table(Order.class);

        InsertStmt insert = dsl.insertInto(orders)
            .columns(orders.col("status"), orders.col("total"))
            .row("NEW", 42)
            .build();
        RenderedSql insertSql = renderer.render(insert, Bindings.empty());
        Long id = db.executeAndReturnGeneratedKey(Command.of(insertSql), "id", rs -> rs.getLong(1));

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

        List<String> afterDelete = db.fetch(Query.of(select, Bindings.of("id", id)), rs -> rs.getString(1));
        assertEquals(List.of(), afterDelete);
    }

    private DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lumen_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private void initializeSchema(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE ORDERS (
                  ID BIGINT AUTO_INCREMENT PRIMARY KEY,
                  STATUS VARCHAR(16) NOT NULL,
                  TOTAL INT NOT NULL
                )
                """);
        }
    }

    private void seedOrders(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO ORDERS (STATUS, TOTAL) VALUES (?, ?)"
             )) {
            insertOrder(statement, "NEW", 10);
            insertOrder(statement, "PAID", 20);
            insertOrder(statement, "NEW", 30);
        }
    }

    private void insertOrder(PreparedStatement statement, String status, int total) throws SQLException {
        statement.setString(1, status);
        statement.setInt(2, total);
        statement.executeUpdate();
    }

    private DefaultDb createDb(DataSource dataSource) {
        EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        EntityNameResolver resolver = EntityNameResolvers.from(Map.of("Order", Order.class));
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        SqlRenderer renderer = new SqlRenderer(dialect);
        return new DefaultDb(
            new io.lighting.lumen.jdbc.JdbcExecutor(dataSource),
            renderer,
            dialect,
            metaRegistry,
            resolver
        );
    }

    @Table(name = "ORDERS")
    private static final class Order {
        @Id
        @Column(name = "ID")
        private Long id;

        @Column(name = "STATUS")
        private String status;

        @Column(name = "TOTAL")
        private int total;
    }

    private record OrderRow(long id, String status) {
    }
}

package io.lighting.lumen.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.lighting.lumen.annotations.SqlConst;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import io.lighting.lumen.template.SqlTemplate;
import io.lighting.lumen.template.TemplateContext;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MysqlCrudExampleTest {
    private static final String MYSQL_URL =
        "jdbc:mysql://localhost:3307/lumen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String MYSQL_USER = "lumen";
    private static final String MYSQL_PASSWORD = "lumen";
    private static final Dialect DIALECT = new LimitOffsetDialect("mysql", "`");

    @SqlConst
    private static final String INSERT_SQL = """
        INSERT INTO @table(Order) (@col(Order::status), @col(Order::total))
        VALUES (:status, :total)
        """;

    private static final String SELECT_TEMPLATE = """
        SELECT @col(Order::id), @col(Order::status), @col(Order::total)
        FROM @table(Order)
        @where {
          @if(id != null) { @col(Order::id) = :id }
          @if(ids) { AND @col(Order::id) IN @in(:ids) }
          @if(statuses) {
            AND (
              @for(s : :statuses) { @or { @col(Order::status) = :s } }
            )
          }
        }
        @orderBy(
          :sort,
          allowed = { ID_ASC : @col(Order::id) ASC, TOTAL_DESC : @col(Order::total) DESC },
          default = ID_ASC
        )
        """;

    @Test
    void runsCrudExamplesWithMysql() throws SQLException {
        DefaultDb db = createDb();
        String initialStatus = "EX" + UUID.randomUUID().toString().substring(0, 8);
        long id = insertWithSqlConst(db, initialStatus, 42);

        List<OrderEntity> inserted = selectWithTemplate(db, id, List.of(initialStatus));
        assertEquals(1, inserted.size());
        assertEquals(initialStatus, inserted.get(0).status);

        updateWithDsl(db, id, "PAID");
        List<OrderEntity> updated = selectWithTemplate(db, id, List.of("PAID"));
        assertEquals(1, updated.size());
        assertEquals("PAID", updated.get(0).status);

        deleteWithRawSql(db, id);
        List<OrderEntity> deleted = selectWithTemplate(db, id, List.of("PAID"));
        assertTrue(deleted.isEmpty());
    }

    private long insertWithSqlConst(DefaultDb db, String status, int total) throws SQLException {
        RenderedSql rendered = render(INSERT_SQL, Bindings.of("status", status, "total", total));
        Long id = db.executeAndReturnGeneratedKey(Command.of(rendered), "id", rs -> rs.getLong(1));
        return id;
    }

    private List<OrderEntity> selectWithTemplate(DefaultDb db, long id, List<String> statuses) throws SQLException {
        RenderedSql rendered = render(
            SELECT_TEMPLATE,
            Bindings.of(
                "id", id,
                "ids", List.of(id),
                "statuses", statuses,
                "sort", "ID_ASC"
            )
        );
        return db.fetch(Query.of(rendered), OrderEntity.class);
    }

    private void updateWithDsl(DefaultDb db, long id, String status) throws SQLException {
        Dsl dsl = new Dsl(metaRegistry());
        io.lighting.lumen.dsl.Table orders = dsl.table(Order.class);
        UpdateStmt update = dsl.update(orders)
            .set(orders.col("status"), status)
            .where(orders.col("id").eq(Dsl.param("id")))
            .build();
        RenderedSql rendered = new SqlRenderer(DIALECT).render(update, Bindings.of("id", id));
        int updated = db.executeOptimistic(Command.of(rendered));
        assertEquals(1, updated);
    }

    private void deleteWithRawSql(DefaultDb db, long id) throws SQLException {
        RenderedSql rendered = new RenderedSql(
            "DELETE FROM orders WHERE id = ?",
            List.of(new Bind.Value(id, 0))
        );
        int deleted = db.execute(Command.of(rendered));
        assertEquals(1, deleted);
    }

    private RenderedSql render(String template, Bindings bindings) {
        SqlTemplate parsed = SqlTemplate.parse(template);
        TemplateContext context = new TemplateContext(
            bindings.asMap(),
            DIALECT,
            metaRegistry(),
            entityResolver()
        );
        return parsed.render(context);
    }

    private DefaultDb createDb() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL_URL);
        dataSource.setUser(MYSQL_USER);
        dataSource.setPassword(MYSQL_PASSWORD);
        EntityMetaRegistry metaRegistry = metaRegistry();
        EntityNameResolver resolver = entityResolver();
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
        return new ReflectionEntityMetaRegistry();
    }

    private EntityNameResolver entityResolver() {
        return EntityNameResolvers.from(Map.of("Order", Order.class));
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

    @Table(name = "orders")
    private static final class OrderEntity {
        @Id
        private long id;

        private String status;

        private int total;
    }
}

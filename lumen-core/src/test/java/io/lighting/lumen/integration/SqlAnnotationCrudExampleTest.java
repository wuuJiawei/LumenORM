package io.lighting.lumen.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.template.annotations.SqlConst;
import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
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
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import io.lighting.lumen.template.TemplateContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class SqlAnnotationCrudExampleTest {
    private static final Dialect DIALECT = new NoQuoteDialect();

    @SqlConst
    private static final String INSERT_SQL = """
        INSERT INTO @table(Order) (@col(Order::status), @col(Order::total))
        VALUES (:status, :total)
        """;

    @SqlConst
    private static final String SELECT_SQL = """
        SELECT @col(Order::id), @col(Order::status)
        FROM @table(Order)
        WHERE @col(Order::id) = :id
        """;

    @SqlConst
    private static final String UPDATE_SQL = """
        UPDATE @table(Order)
        SET @col(Order::status) = :status
        WHERE @col(Order::id) = :id
        """;

    @SqlConst
    private static final String DELETE_SQL = """
        DELETE FROM @table(Order)
        WHERE @col(Order::id) = :id
        """;

    private static final String TEMPLATE_INSERT = """
        INSERT INTO @table(Order) (@col(Order::status), @col(Order::total))
        VALUES (:status, :total)
        """;

    private static final String TEMPLATE_SELECT = """
        SELECT @col(Order::id), @col(Order::status)
        FROM @table(Order)
        WHERE @col(Order::id) = :id
        """;

    private static final String TEMPLATE_UPDATE = """
        UPDATE @table(Order)
        SET @col(Order::status) = :status
        WHERE @col(Order::id) = :id
        """;

    private static final String TEMPLATE_DELETE = """
        DELETE FROM @table(Order)
        WHERE @col(Order::id) = :id
        """;

    private static final String TEMPLATE_DYNAMIC_RAW = """
        SELECT id, status, total
        FROM orders
        @where {
          @if(status != null) { status = :status }
          @if(ids) { AND id IN @in(:ids) }
          @if(statuses) {
            AND (
              @for(s : :statuses) { @or { status = :s } }
            )
          }
        }
        @orderBy(:sort, allowed = { ID_ASC : id ASC, ID_DESC : id DESC }, default = ID_ASC)
        """;

    @Test
    void crudWithSqlConst() throws SQLException {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        DefaultDb db = createDb(dataSource);

        RenderedSql insert = render(INSERT_SQL, Bindings.of("status", "NEW", "total", 10));
        long id = db.executeAndReturnGeneratedKey(Command.of(insert), "ID", rs -> rs.getLong(1));

        RenderedSql update = render(UPDATE_SQL, Bindings.of("id", id, "status", "PAID"));
        db.executeOptimistic(Command.of(update));

        List<OrderRow> rows = db.run(SELECT_SQL, Bindings.of("id", id), rs -> new OrderRow(rs.getLong(1), rs.getString(2)));
        assertEquals(List.of(new OrderRow(id, "PAID")), rows);

        RenderedSql delete = render(DELETE_SQL, Bindings.of("id", id));
        int deleted = db.execute(Command.of(delete));
        assertEquals(1, deleted);

        List<OrderRow> afterDelete = db.run(SELECT_SQL, Bindings.of("id", id), rs -> new OrderRow(rs.getLong(1), rs.getString(2)));
        assertEquals(List.of(), afterDelete);
    }

    @Test
    void crudWithSqlTemplate() throws SQLException {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        DefaultDb db = createDb(dataSource);
        OrderTemplateDao dao = new ManualOrderTemplateDao();

        RenderedSql insert = dao.insert("NEW", 20);
        long id = db.executeAndReturnGeneratedKey(Command.of(insert), "ID", rs -> rs.getLong(1));

        RenderedSql update = dao.update(id, "PAID");
        db.executeOptimistic(Command.of(update));

        RenderedSql select = dao.select(id);
        List<OrderRow> rows = db.fetch(Query.of(select), rs -> new OrderRow(rs.getLong(1), rs.getString(2)));
        assertEquals(List.of(new OrderRow(id, "PAID")), rows);

        RenderedSql delete = dao.delete(id);
        int deleted = db.execute(Command.of(delete));
        assertEquals(1, deleted);
    }

    @Test
    void templateWithIfForAndRawColumns() throws SQLException {
        DataSource dataSource = dataSource();
        initializeSchema(dataSource);
        DefaultDb db = createDb(dataSource);
        OrderTemplateDao dao = new ManualOrderTemplateDao();

        long firstId = insertOrder(db, "NEW", 10);
        long secondId = insertOrder(db, "PAID", 20);
        long thirdId = insertOrder(db, "NEW", 30);

        RenderedSql rendered = dao.search(
            null,
            List.of(firstId, secondId, thirdId),
            List.of("NEW"),
            "ID_ASC"
        );
        List<OrderEntity> rows = db.fetch(Query.of(rendered), OrderEntity.class);

        assertEquals(2, rows.size());
        assertEquals("NEW", rows.get(0).status);
        assertEquals("NEW", rows.get(1).status);
    }

    private RenderedSql render(String template, Bindings bindings) {
        EntityMetaRegistry metaRegistry = new TestEntityMetaRegistry();
        EntityNameResolver resolver = EntityNameResolvers.from(Map.of("Order", Order.class));
        TemplateContext context = new TemplateContext(bindings.asMap(), DIALECT, metaRegistry, resolver);
        return io.lighting.lumen.template.SqlTemplate.parse(template).render(context);
    }

    private DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lumen_sql_annotation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
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

    private DefaultDb createDb(DataSource dataSource) {
        EntityMetaRegistry metaRegistry = new TestEntityMetaRegistry();
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

    private long insertOrder(DefaultDb db, String status, int total) throws SQLException {
        RenderedSql insert = render(INSERT_SQL, Bindings.of("status", status, "total", total));
        Long id = db.executeAndReturnGeneratedKey(Command.of(insert), "ID", rs -> rs.getLong(1));
        return id;
    }

    interface OrderTemplateDao {
        @SqlTemplate(TEMPLATE_INSERT)
        RenderedSql insert(String status, int total) throws SQLException;

        @SqlTemplate(TEMPLATE_UPDATE)
        RenderedSql update(long id, String status) throws SQLException;

        @SqlTemplate(TEMPLATE_SELECT)
        RenderedSql select(long id) throws SQLException;

        @SqlTemplate(TEMPLATE_DELETE)
        RenderedSql delete(long id) throws SQLException;

        @SqlTemplate(TEMPLATE_DYNAMIC_RAW)
        RenderedSql search(String status, List<Long> ids, List<String> statuses, String sort) throws SQLException;
    }

    private final class ManualOrderTemplateDao implements OrderTemplateDao {
        @Override
        public RenderedSql insert(String status, int total) {
            return render(TEMPLATE_INSERT, Bindings.of("status", status, "total", total));
        }

        @Override
        public RenderedSql update(long id, String status) {
            return render(TEMPLATE_UPDATE, Bindings.of("id", id, "status", status));
        }

        @Override
        public RenderedSql select(long id) {
            return render(TEMPLATE_SELECT, Bindings.of("id", id));
        }

        @Override
        public RenderedSql delete(long id) {
            return render(TEMPLATE_DELETE, Bindings.of("id", id));
        }

        @Override
        public RenderedSql search(String status, List<Long> ids, List<String> statuses, String sort) {
            return render(
                TEMPLATE_DYNAMIC_RAW,
                Bindings.of(
                    "status", status,
                    "ids", ids,
                    "statuses", statuses,
                    "sort", sort
                )
            );
        }
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
            throw new UnsupportedOperationException("Pagination not supported in SQL annotation examples");
        }
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

    @Table(name = "ORDERS")
    private static final class OrderEntity {
        @Id
        private long id;

        private String status;

        private int total;
    }

    private record OrderRow(long id, String status) {
    }
}

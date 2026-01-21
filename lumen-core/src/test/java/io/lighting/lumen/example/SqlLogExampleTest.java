package io.lighting.lumen.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.db.SqlLog;
import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqlLogExampleTest {
    @Test
    void logsInlineSql() throws SQLException {
        List<String> logs = new ArrayList<>();
        DefaultDb db = createDb(logs, SqlLog.Mode.INLINE);
        prepareSchema(db, logs);

        RenderedSql insert = new RenderedSql(
            "INSERT INTO orders(status, total) VALUES (?, ?)",
            List.of(new Bind.Value("NEW", 0), new Bind.Value(10, 0))
        );
        db.execute(Command.of(insert));

        logs.forEach(System.out::println);
        assertTrue(logs.get(0).contains("INSERT INTO orders"));
        assertTrue(logs.get(0).contains("'NEW'"));
    }

    @Test
    void logsSqlAndBindsSeparately() throws SQLException {
        List<String> logs = new ArrayList<>();
        DefaultDb db = createDb(logs, SqlLog.Mode.SEPARATE);
        prepareSchema(db, logs);

        RenderedSql select = new RenderedSql(
            "SELECT status FROM orders WHERE total > ?",
            List.of(new Bind.Value(5, 0))
        );
        db.fetch(Query.of(select), rs -> rs.getString(1));

        logs.forEach(System.out::println);
        assertTrue(logs.get(0).contains("SELECT status FROM orders"));
        assertTrue(logs.get(0).contains("binds=[5]"));
    }

    @Test
    void logsViaSlf4jSink() throws SQLException {
        Logger logger = LoggerFactory.getLogger(SqlLogExampleTest.class);
        List<String> logs = new ArrayList<>();
        DefaultDb db = createDb(message -> {
            logs.add(message);
            logger.info(message);
        }, SqlLog.Mode.INLINE);
        prepareSchema(db, logs);

        RenderedSql insert = new RenderedSql(
            "INSERT INTO orders(status, total) VALUES (?, ?)",
            List.of(new Bind.Value("NEW", 0), new Bind.Value(10, 0))
        );
        db.execute(Command.of(insert));

        assertTrue(logs.get(0).contains("INSERT INTO orders"));
    }

    private DefaultDb createDb(List<String> logs, SqlLog.Mode mode) {
        return createDb(logs::add, mode);
    }

    private DefaultDb createDb(java.util.function.Consumer<String> sink, SqlLog.Mode mode) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:log_example_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcExecutor executor = new JdbcExecutor(dataSource);
        Dialect dialect = new NoQuoteDialect();
        SqlRenderer renderer = new SqlRenderer(dialect);
        EntityMetaRegistry metaRegistry = new TestEntityMetaRegistry();
        EntityNameResolver resolver = EntityNameResolvers.from(Map.of());
        SqlLog observer = SqlLog.builder()
            .mode(mode)
            .includeOperation(false)
            .sink(sink)
            .build();
        return new DefaultDb(executor, renderer, dialect, metaRegistry, resolver, List.of(observer));
    }

    private void prepareSchema(DefaultDb db, List<String> logs) throws SQLException {
        db.execute(Command.of(new RenderedSql("DROP TABLE IF EXISTS orders", List.of())));
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(16), total INT)",
            List.of()
        )));
        logs.clear();
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
            throw new UnsupportedOperationException("Pagination not supported in SQL log examples");
        }
    }
}

package io.lighting.lumen.sql.dialect;

import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialectResolverTest {
    @Test
    void resolvesLimitOffsetDialects() {
        assertDialect("mariadb", "MariaDB", "MariaDB JDBC", "jdbc:mariadb://", "`id`");
        assertDialect("mysql", "MySQL", "MySQL Connector/J", "jdbc:mysql://", "`id`");
        assertDialect("postgres", "PostgreSQL", "PostgreSQL JDBC Driver", "jdbc:postgresql://", "\"id\"");
        assertDialect("h2", "H2", "H2 JDBC Driver", "jdbc:h2:mem:", "\"id\"");
        assertDialect("sqlite", "SQLite", "SQLite JDBC", "jdbc:sqlite::memory:", "\"id\"");
    }

    @Test
    void resolvesOracleDialect() {
        Dialect dialect = DialectResolver.resolve("Oracle", "Oracle JDBC", "jdbc:oracle:thin:@localhost");
        assertEquals("oracle", dialect.id());
        assertEquals("\"id\"", dialect.quoteIdent("id"));
        RenderedPagination pagination = dialect.renderPagination(2, 10, List.of());
        assertEquals(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ", pagination.sqlFragment());
        assertEquals(2, pagination.binds().size());
    }

    @Test
    void resolvesSqlServerDialect() {
        Dialect dialect = DialectResolver.resolve(
            "Microsoft SQL Server",
            "Microsoft JDBC Driver for SQL Server",
            "jdbc:sqlserver://localhost"
        );
        assertEquals("sqlserver", dialect.id());
        assertEquals("[id]", dialect.quoteIdent("id"));
        assertThrows(IllegalArgumentException.class, () -> dialect.renderPagination(1, 10, List.of()));
        RenderedPagination pagination = dialect.renderPagination(
            1,
            10,
            List.of(new OrderItem(new Expr.Column("t", "id"), true))
        );
        assertEquals(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ", pagination.sqlFragment());
    }

    @Test
    void fallsBackToAnsi() {
        Dialect dialect = DialectResolver.resolve("UnknownDB", "UnknownDriver", "jdbc:unknown://");
        assertEquals("ansi", dialect.id());
        assertEquals("\"id\"", dialect.quoteIdent("id"));
    }

    private static void assertDialect(
        String expectedId,
        String productName,
        String driverName,
        String url,
        String quoted
    ) {
        Dialect dialect = DialectResolver.resolve(productName, driverName, url);
        assertEquals(expectedId, dialect.id());
        assertEquals(quoted, dialect.quoteIdent("id"));
    }
}

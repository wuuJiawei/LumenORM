package io.lighting.lumen.sql.dialect;

import io.lighting.lumen.sql.Dialect;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import javax.sql.DataSource;

public final class DialectResolver {
    private DialectResolver() {
    }

    public static Dialect resolve(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return resolve(meta.getDatabaseProductName(), meta.getDriverName(), meta.getURL());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to resolve dialect from dataSource", ex);
        }
    }

    static Dialect resolve(String productName, String driverName, String url) {
        String product = normalize(productName);
        String driver = normalize(driverName);
        String jdbcUrl = normalize(url);
        if (matches(product, driver, jdbcUrl, "mariadb")) {
            return new LimitOffsetDialect("mariadb", "`");
        }
        if (matches(product, driver, jdbcUrl, "mysql")) {
            return new LimitOffsetDialect("mysql", "`");
        }
        if (matches(product, driver, jdbcUrl, "postgres")) {
            return new LimitOffsetDialect("postgres", "\"");
        }
        if (matches(product, driver, jdbcUrl, "h2")) {
            return new LimitOffsetDialect("h2", "\"");
        }
        if (matches(product, driver, jdbcUrl, "sqlite")) {
            return new LimitOffsetDialect("sqlite", "\"");
        }
        if (matches(product, driver, jdbcUrl, "oracle")) {
            return new OffsetFetchDialect("oracle", "\"", "\"", false);
        }
        if (matches(product, driver, jdbcUrl, "sql server") || matches(product, driver, jdbcUrl, "sqlserver")) {
            return new OffsetFetchDialect("sqlserver", "[", "]", true);
        }
        return new LimitOffsetDialect("ansi", "\"");
    }

    private static boolean matches(String product, String driver, String url, String token) {
        return product.contains(token) || driver.contains(token) || url.contains(token);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

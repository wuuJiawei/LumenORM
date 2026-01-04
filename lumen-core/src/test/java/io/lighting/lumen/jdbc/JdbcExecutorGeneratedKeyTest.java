package io.lighting.lumen.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcExecutorGeneratedKeyTest {

    @Test
    void returnsGeneratedKey() throws SQLException {
        ResultSetHandler keys = new ResultSetHandler(List.<Object[]>of(new Object[] { 10L }));
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(keys.proxy());
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler);
        JdbcExecutor executor = new JdbcExecutor(new DataSourceHandler(connectionHandler.proxy()).proxy());
        RenderedSql rendered = new RenderedSql("INSERT INTO t(v) VALUES (?)", List.of(new Bind.Value("x", 0)));

        Long id = executor.executeAndReturnGeneratedKey(rendered, "id", rs -> rs.getLong(1));

        assertEquals(10L, id);
        assertEquals(List.of("id"), statementHandler.generatedColumns());
        assertEquals(1, statementHandler.executeUpdateCalls());
        assertEquals(1, keys.nextCalls());
        assertEquals(true, keys.closed());
    }

    @Test
    void throwsWhenNoGeneratedKey() throws SQLException {
        ResultSetHandler keys = new ResultSetHandler(List.<Object[]>of());
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(keys.proxy());
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler);
        JdbcExecutor executor = new JdbcExecutor(new DataSourceHandler(connectionHandler.proxy()).proxy());
        RenderedSql rendered = new RenderedSql("INSERT INTO t(v) VALUES (?)", List.of(new Bind.Value("x", 0)));

        assertThrows(
            IllegalStateException.class,
            () -> executor.executeAndReturnGeneratedKey(rendered, "id", rs -> rs.getLong(1))
        );
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<Object[]> rows;
        private int index = -1;
        private int nextCalls;
        private boolean closed;

        private ResultSetHandler(List<Object[]> rows) {
            this.rows = rows;
        }

        private ResultSet proxy() {
            return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                this
            );
        }

        private int nextCalls() {
            return nextCalls;
        }

        private boolean closed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "next" -> {
                    nextCalls++;
                    index++;
                    yield index < rows.size();
                }
                case "getLong" -> {
                    int column = (int) args[0];
                    yield ((Number) rows.get(index)[column - 1]).longValue();
                }
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "isClosed" -> closed;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final ResultSet generatedKeys;
        private int executeUpdateCalls;
        private List<String> generatedColumns = List.of();
        private boolean closed;

        private PreparedStatementHandler(ResultSet generatedKeys) {
            this.generatedKeys = generatedKeys;
        }

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this
            );
        }

        private int executeUpdateCalls() {
            return executeUpdateCalls;
        }

        private List<String> generatedColumns() {
            return generatedColumns;
        }

        private void setGeneratedColumns(String[] cols) {
            generatedColumns = List.of(cols);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "setObject", "setNull" -> null;
                case "executeUpdate" -> {
                    executeUpdateCalls++;
                    yield 1;
                }
                case "getGeneratedKeys" -> generatedKeys;
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "isClosed" -> closed;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final PreparedStatementHandler statementHandler;
        private boolean closed;

        private ConnectionHandler(PreparedStatementHandler statementHandler) {
            this.statementHandler = statementHandler;
        }

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            if (method.getName().equals("prepareStatement")
                && args.length == 2
                && args[0] instanceof String
                && args[1] instanceof String[] cols) {
                statementHandler.setGeneratedColumns(cols);
                return statementHandler.proxy();
            }
            return switch (method.getName()) {
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "isClosed" -> closed;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class DataSourceHandler implements InvocationHandler {
        private final Connection connection;

        private DataSourceHandler(Connection connection) {
            this.connection = connection;
        }

        private DataSource proxy() {
            return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "getConnection" -> connection;
                case "getLogWriter" -> null;
                case "setLogWriter", "setLoginTimeout" -> null;
                case "getLoginTimeout" -> 0;
                case "getParentLogger" -> java.util.logging.Logger.getLogger("test");
                case "isWrapperFor" -> false;
                case "unwrap" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> proxy.getClass().getName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }
}

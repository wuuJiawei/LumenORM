package io.lighting.lumen.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcExecutorStreamTest {

    @Test
    void streamsRowsWithFetchSize() throws SQLException {
        ResultSetHandler resultSetHandler = new ResultSetHandler(List.of(new Object[] { 1 }, new Object[] { 2 }));
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy());
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        JdbcExecutor executor = new JdbcExecutor(new DataSourceHandler(connectionHandler.proxy()).proxy());
        RenderedSql renderedSql = new RenderedSql("SELECT ? AS id", List.of(new Bind.Value(1, 0)));

        List<Integer> results = new ArrayList<>();
        try (ResultStream<Integer> stream = executor.fetchStream(renderedSql, rs -> rs.getInt(1), 50)) {
            while (stream.next()) {
                results.add(stream.row());
            }
        }

        assertEquals(List.of(1, 2), results);
        assertEquals(50, statementHandler.fetchSize());
        assertTrue(resultSetHandler.closed());
        assertTrue(statementHandler.closed());
        assertTrue(connectionHandler.closed());
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<Object[]> rows;
        private int index = -1;
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
                    index++;
                    yield index < rows.size();
                }
                case "getInt" -> {
                    int column = (int) args[0];
                    yield ((Number) rows.get(index)[column - 1]).intValue();
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
        private final ResultSet resultSet;
        private boolean closed;
        private int fetchSize;

        private PreparedStatementHandler(ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this
            );
        }

        private boolean closed() {
            return closed;
        }

        private int fetchSize() {
            return fetchSize;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "setFetchSize" -> {
                    fetchSize = (int) args[0];
                    yield null;
                }
                case "setObject", "setNull" -> null;
                case "executeQuery" -> resultSet;
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
        private final PreparedStatement preparedStatement;
        private boolean closed;

        private ConnectionHandler(PreparedStatement preparedStatement) {
            this.preparedStatement = preparedStatement;
        }

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                this
            );
        }

        private boolean closed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "prepareStatement" -> preparedStatement;
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

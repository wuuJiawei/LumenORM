package io.lighting.lumen.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcExecutorBatchTest {

    @Test
    void executesBatchesInChunks() throws SQLException {
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(
            List.of(new int[] { 1, 1 }, new int[] { 1 })
        );
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        JdbcExecutor executor = new JdbcExecutor(new DataSourceHandler(connectionHandler.proxy()).proxy());
        RenderedSql template = new RenderedSql("UPDATE t SET v=?", List.of());

        int[] results = executor.executeBatch(
            template,
            List.of(
                List.of(new Bind.Value(1, 0)),
                List.of(new Bind.Value(2, 0)),
                List.of(new Bind.Value(3, 0))
            ),
            2
        );

        assertArrayEquals(new int[] { 1, 1, 1 }, results);
        assertEquals(2, statementHandler.executeBatchCalls());
        assertEquals(
            List.of(
                List.of(new BoundParam(1, 0, "setObject")),
                List.of(new BoundParam(2, 0, "setObject")),
                List.of(new BoundParam(3, 0, "setObject"))
            ),
            statementHandler.batchParams()
        );
    }

    @Test
    void executeBatchHandlesEmptyInput() throws SQLException {
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(List.of());
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        JdbcExecutor executor = new JdbcExecutor(new DataSourceHandler(connectionHandler.proxy()).proxy());
        RenderedSql template = new RenderedSql("UPDATE t SET v=?", List.of());

        int[] results = executor.executeBatch(template, List.of(), 2);

        assertArrayEquals(new int[0], results);
        assertEquals(0, statementHandler.executeBatchCalls());
    }

    private record BoundParam(Object value, int jdbcType, String method) {
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final List<int[]> updateBatches;
        private final List<List<BoundParam>> batchParams = new ArrayList<>();
        private final List<BoundParam> current = new ArrayList<>();
        private int executeBatchCalls;
        private boolean closed;

        private PreparedStatementHandler(List<int[]> updateBatches) {
            this.updateBatches = updateBatches;
        }

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this
            );
        }

        private int executeBatchCalls() {
            return executeBatchCalls;
        }

        private List<List<BoundParam>> batchParams() {
            return List.copyOf(batchParams);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            switch (method.getName()) {
                case "setObject" -> {
                    int index = (int) args[0];
                    Object value = args[1];
                    int jdbcType = args.length == 3 ? (int) args[2] : 0;
                    ensureSize(current, index);
                    current.set(index - 1, new BoundParam(value, jdbcType, "setObject"));
                    return null;
                }
                case "setNull" -> {
                    int index = (int) args[0];
                    int jdbcType = (int) args[1];
                    ensureSize(current, index);
                    current.set(index - 1, new BoundParam(null, jdbcType, "setNull"));
                    return null;
                }
                case "addBatch" -> {
                    batchParams.add(List.copyOf(current));
                    current.clear();
                    return null;
                }
                case "executeBatch" -> {
                    executeBatchCalls++;
                    int batchIndex = executeBatchCalls - 1;
                    if (batchIndex >= updateBatches.size()) {
                        return new int[0];
                    }
                    return updateBatches.get(batchIndex).clone();
                }
                case "close" -> {
                    closed = true;
                    return null;
                }
                case "isClosed" -> {
                    return closed;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        }

        private void ensureSize(List<BoundParam> params, int index) {
            while (params.size() < index) {
                params.add(null);
            }
        }
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final PreparedStatement preparedStatement;

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

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "prepareStatement" -> preparedStatement;
                case "close" -> null;
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

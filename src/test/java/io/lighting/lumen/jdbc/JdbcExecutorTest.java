package io.lighting.lumen.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcExecutorTest {

    @Test
    void fetchBindsValuesAndMapsRows() throws SQLException {
        ResultSetHandler resultSetHandler = new ResultSetHandler(
            List.of(new Object[] { "alpha" }, new Object[] { "beta" }),
            Map.of("name", 1)
        );
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy(), 0);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        DataSource dataSource = new DataSourceHandler(connectionHandler.proxy()).proxy();
        JdbcExecutor executor = new JdbcExecutor(dataSource);
        RenderedSql renderedSql = new RenderedSql(
            "SELECT name FROM t WHERE a=? AND b=? AND c=?",
            List.of(
                new Bind.Value("active", Types.VARCHAR),
                new Bind.NullValue(Types.INTEGER),
                new Bind.Value(42, 0)
            )
        );

        List<Object> results = executor.fetch(renderedSql, rs -> rs.getObject(1));

        assertEquals(List.of("alpha", "beta"), results);
        assertEquals(
            List.of(
                new BoundParam("active", Types.VARCHAR, "setObject"),
                new BoundParam(null, Types.INTEGER, "setNull"),
                new BoundParam(42, 0, "setObject")
            ),
            statementHandler.boundParams()
        );
        assertTrue(resultSetHandler.isClosed());
        assertTrue(statementHandler.isClosed());
        assertTrue(connectionHandler.isClosed());
    }

    @Test
    void executeBindsValuesAndReturnsUpdateCount() throws SQLException {
        ResultSetHandler resultSetHandler = new ResultSetHandler(List.of(), Map.of());
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy(), 3);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        DataSource dataSource = new DataSourceHandler(connectionHandler.proxy()).proxy();
        JdbcExecutor executor = new JdbcExecutor(dataSource);
        RenderedSql renderedSql = new RenderedSql(
            "UPDATE t SET name=? WHERE id=? AND deleted_at IS ?",
            List.of(
                new Bind.Value("lamp", Types.VARCHAR),
                new Bind.Value(9, 0),
                new Bind.NullValue(0)
            )
        );

        int rows = executor.execute(renderedSql);

        assertEquals(3, rows);
        assertEquals(
            List.of(
                new BoundParam("lamp", Types.VARCHAR, "setObject"),
                new BoundParam(9, 0, "setObject"),
                new BoundParam(null, Types.NULL, "setNull")
            ),
            statementHandler.boundParams()
        );
        assertTrue(statementHandler.isClosed());
        assertTrue(connectionHandler.isClosed());
    }

    private record BoundParam(Object value, int jdbcType, String method) {
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<Object[]> rows;
        private final Map<String, Integer> labels;
        private int index = -1;
        private boolean closed;

        private ResultSetHandler(List<Object[]> rows, Map<String, Integer> labels) {
            this.rows = rows;
            this.labels = labels;
        }

        private ResultSet proxy() {
            return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                this
            );
        }

        private boolean isClosed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            switch (method.getName()) {
                case "next" -> {
                    index++;
                    return index < rows.size();
                }
                case "getObject" -> {
                    Object value = resolveValue(args[0]);
                    if (args.length == 2 && args[1] instanceof Class<?> type) {
                        return type.cast(value);
                    }
                    return value;
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

        private Object resolveValue(Object key) throws SQLException {
            if (key instanceof Integer indexKey) {
                return rows.get(index)[indexKey - 1];
            }
            if (key instanceof String label) {
                Integer labelIndex = labels.get(label);
                if (labelIndex == null) {
                    throw new SQLException("Unknown label: " + label);
                }
                return rows.get(index)[labelIndex - 1];
            }
            throw new SQLException("Unsupported key: " + key);
        }
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final List<BoundParam> boundParams = new ArrayList<>();
        private final ResultSet resultSet;
        private final int updateCount;
        private boolean closed;

        private PreparedStatementHandler(ResultSet resultSet, int updateCount) {
            this.resultSet = resultSet;
            this.updateCount = updateCount;
        }

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this
            );
        }

        private List<BoundParam> boundParams() {
            return List.copyOf(boundParams);
        }

        private boolean isClosed() {
            return closed;
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
                    int jdbcType = args.length == 3 ? resolveJdbcType(args[2]) : 0;
                    setParam(index, new BoundParam(value, jdbcType, "setObject"));
                    return null;
                }
                case "setNull" -> {
                    int index = (int) args[0];
                    int jdbcType = (int) args[1];
                    setParam(index, new BoundParam(null, jdbcType, "setNull"));
                    return null;
                }
                case "executeQuery" -> {
                    return resultSet;
                }
                case "executeUpdate" -> {
                    return updateCount;
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

        private void setParam(int index, BoundParam param) {
            int zeroIndex = index - 1;
            while (boundParams.size() <= zeroIndex) {
                boundParams.add(null);
            }
            boundParams.set(zeroIndex, param);
        }

        private int resolveJdbcType(Object type) {
            if (type instanceof Integer intType) {
                return intType;
            }
            return Types.OTHER;
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

        private boolean isClosed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            switch (method.getName()) {
                case "prepareStatement" -> {
                    return preparedStatement;
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
            switch (method.getName()) {
                case "getConnection" -> {
                    return connection;
                }
                case "getLogWriter" -> {
                    return new PrintWriter(System.out);
                }
                case "setLogWriter" -> {
                    return null;
                }
                case "getLoginTimeout" -> {
                    return 0;
                }
                case "setLoginTimeout" -> {
                    return null;
                }
                case "getParentLogger" -> {
                    return java.util.logging.Logger.getLogger("test");
                }
                case "isWrapperFor" -> {
                    return false;
                }
                case "unwrap" -> {
                    return null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
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

package io.lighting.lumen.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.TableRef;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.sql.function.FunctionRegistry;
import io.lighting.lumen.template.EntityNameResolvers;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DefaultDbTest {

    @Test
    void fetchRendersQueryAndExecutes() throws SQLException {
        AtomicReference<String> sqlCapture = new AtomicReference<>();
        ResultSetHandler resultSetHandler = new ResultSetHandler(List.<Object[]>of(new Object[] { 7 }));
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy(), 0);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy(), sqlCapture);
        DefaultDb db = createDb(connectionHandler.proxy());
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.Column("o", "id"), null)),
            new TableRef("orders", "o"),
            List.of(),
            new Expr.Compare(new Expr.Column("o", "id"), Expr.Op.EQ, new Expr.Param("id")),
            List.of(),
            null,
            List.of(),
            null
        );

        List<Object> results = db.fetch(Query.of(stmt, Bindings.of("id", 7)), rs -> rs.getObject(1));

        assertEquals(List.of(7), results);
        assertEquals("SELECT \"o\".\"id\" FROM \"orders\" \"o\" WHERE \"o\".\"id\" = ?", sqlCapture.get());
        assertEquals(List.of(new BoundParam(7, 0, "setObject")), statementHandler.boundParams());
    }

    @Test
    void runParsesTemplateAndExecutes() throws SQLException {
        AtomicReference<String> sqlCapture = new AtomicReference<>();
        ResultSetHandler resultSetHandler = new ResultSetHandler(List.<Object[]>of(new Object[] { "ok" }));
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy(), 0);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy(), sqlCapture);
        DefaultDb db = createDb(connectionHandler.proxy());

        List<Object> results = db.run("SELECT :id", Bindings.of("id", 3), rs -> rs.getObject(1));

        assertEquals(List.of("ok"), results);
        assertEquals("SELECT ?", sqlCapture.get());
        assertEquals(List.of(new BoundParam(3, 0, "setObject")), statementHandler.boundParams());
    }

    @Test
    void executeUsesCommand() throws SQLException {
        AtomicReference<String> sqlCapture = new AtomicReference<>();
        ResultSetHandler resultSetHandler = new ResultSetHandler(List.<Object[]>of());
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(resultSetHandler.proxy(), 2);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy(), sqlCapture);
        DefaultDb db = createDb(connectionHandler.proxy());
        RenderedSql renderedSql = new RenderedSql(
            "UPDATE t SET name=?",
            List.of(new Bind.Value("lamp", 0))
        );

        int updated = db.execute(Command.of(renderedSql));

        assertEquals(2, updated);
        assertEquals("UPDATE t SET name=?", sqlCapture.get());
        assertEquals(List.of(new BoundParam("lamp", 0, "setObject")), statementHandler.boundParams());
    }

    private DefaultDb createDb(Connection connection) {
        DataSource dataSource = new DataSourceHandler(connection).proxy();
        JdbcExecutor executor = new JdbcExecutor(dataSource);
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        SqlRenderer renderer = new SqlRenderer(dialect);
        return new DefaultDb(
            executor,
            renderer,
            dialect,
            new ReflectionEntityMetaRegistry(),
            EntityNameResolvers.from(Map.of()),
            FunctionRegistry.standard()
        );
    }

    private record BoundParam(Object value, int jdbcType, String method) {
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final List<Object[]> rows;
        private int index = -1;

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
                    int columnIndex = (int) args[0];
                    return rows.get(index)[columnIndex - 1];
                }
                case "close" -> {
                    return null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
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
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final PreparedStatement preparedStatement;
        private final AtomicReference<String> sqlCapture;
        private boolean closed;

        private ConnectionHandler(PreparedStatement preparedStatement, AtomicReference<String> sqlCapture) {
            this.preparedStatement = preparedStatement;
            this.sqlCapture = sqlCapture;
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
            if (method.getName().equals("prepareStatement") && args.length > 0 && args[0] instanceof String sql) {
                sqlCapture.set(sql);
                return preparedStatement;
            }
            if (method.getName().equals("close")) {
                closed = true;
                return null;
            }
            if (method.getName().equals("isClosed")) {
                return closed;
            }
            throw new UnsupportedOperationException(method.getName());
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
                case "getLogWriter" -> new PrintWriter(System.out);
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

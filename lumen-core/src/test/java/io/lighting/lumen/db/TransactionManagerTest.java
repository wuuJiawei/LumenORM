package io.lighting.lumen.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolvers;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TransactionManagerTest {

    @Test
    void commitsTransactionOnSuccess() throws SQLException {
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(1);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        TransactionManager manager = createManager(connectionHandler.proxy());

        int updated = manager.inTransaction(db -> db.execute(
            Command.of(new RenderedSql("UPDATE t SET v=?", List.of(new Bind.Value(1, 0))))
        ));

        assertEquals(1, updated);
        assertTrue(connectionHandler.commitCalled());
        assertFalse(connectionHandler.rollbackCalled());
        assertEquals(List.of(false, true), connectionHandler.autoCommitChanges());
    }

    @Test
    void rollsBackOnException() {
        PreparedStatementHandler statementHandler = new PreparedStatementHandler(1);
        ConnectionHandler connectionHandler = new ConnectionHandler(statementHandler.proxy());
        TransactionManager manager = createManager(connectionHandler.proxy());

        assertThrows(RuntimeException.class, () -> manager.inTransaction(db -> {
            db.execute(Command.of(new RenderedSql("UPDATE t SET v=?", List.of(new Bind.Value(1, 0)))));
            throw new RuntimeException("boom");
        }));

        assertFalse(connectionHandler.commitCalled());
        assertTrue(connectionHandler.rollbackCalled());
        assertEquals(List.of(false, true), connectionHandler.autoCommitChanges());
    }

    private TransactionManager createManager(Connection connection) {
        DataSource dataSource = new DataSourceHandler(connection).proxy();
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        SqlRenderer renderer = new SqlRenderer(dialect);
        return new TransactionManager(
            dataSource,
            renderer,
            dialect,
            new TestEntityMetaRegistry(),
            EntityNameResolvers.from(Map.of())
        );
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final int updateCount;
        private boolean closed;

        private PreparedStatementHandler(int updateCount) {
            this.updateCount = updateCount;
        }

        private PreparedStatement proxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "setObject", "setNull" -> null;
                case "executeUpdate" -> updateCount;
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
        private final List<Boolean> autoCommitChanges = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean commitCalled;
        private boolean rollbackCalled;

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

        private boolean commitCalled() {
            return commitCalled;
        }

        private boolean rollbackCalled() {
            return rollbackCalled;
        }

        private List<Boolean> autoCommitChanges() {
            return List.copyOf(autoCommitChanges);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "prepareStatement" -> preparedStatement;
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) args[0];
                    autoCommitChanges.add(autoCommit);
                    yield null;
                }
                case "commit" -> {
                    commitCalled = true;
                    yield null;
                }
                case "rollback" -> {
                    rollbackCalled = true;
                    yield null;
                }
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

package io.lighting.lumen;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.DbObserver;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

public final class Lumen {
    private final Db db;
    private final Dsl dsl;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;
    private final EntityNameResolver entityNameResolver;
    private final SqlRenderer renderer;
    private final Map<Class<?>, Object> daoCache = new ConcurrentHashMap<>();

    private Lumen(
        Db db,
        Dsl dsl,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver,
        SqlRenderer renderer
    ) {
        this.db = db;
        this.dsl = dsl;
        this.dialect = dialect;
        this.metaRegistry = metaRegistry;
        this.entityNameResolver = entityNameResolver;
        this.renderer = renderer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T dao(Class<T> daoType) {
        Objects.requireNonNull(daoType, "daoType");
        if (!daoType.isInterface()) {
            throw new IllegalArgumentException("DAO type must be an interface: " + daoType.getName());
        }
        Object instance = daoCache.computeIfAbsent(daoType, this::newDaoInstance);
        return daoType.cast(instance);
    }

    public Dsl dsl() {
        return dsl;
    }

    public Db db() {
        return db;
    }

    public Dialect dialect() {
        return dialect;
    }

    public EntityMetaRegistry metaRegistry() {
        return metaRegistry;
    }

    public EntityNameResolver entityNameResolver() {
        return entityNameResolver;
    }

    public SqlRenderer renderer() {
        return renderer;
    }

    private Object newDaoInstance(Class<?> daoType) {
        String implName = daoType.getName() + "_Impl";
        try {
            Class<?> implClass = Class.forName(implName, true, daoType.getClassLoader());
            Constructor<?> ctor = implClass.getConstructor(
                Db.class,
                Dialect.class,
                EntityMetaRegistry.class,
                EntityNameResolver.class
            );
            return ctor.newInstance(db, dialect, metaRegistry, entityNameResolver);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("DAO implementation not found: " + implName, ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create DAO implementation: " + implName, ex);
        }
    }

    public static final class Builder {
        private DataSource dataSource;
        private Db db;
        private Dialect dialect = new LimitOffsetDialect("\"");
        private EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        private EntityNameResolver entityNameResolver = EntityNameResolvers.from(Map.of());
        private SqlRenderer renderer;
        private List<DbObserver> observers = List.of();

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
            return this;
        }

        public Builder db(Db db) {
            this.db = Objects.requireNonNull(db, "db");
            return this;
        }

        // 方言：负责分页语法与标识符引用，不同数据库需要不同实现。
        public Builder dialect(Dialect dialect) {
            this.dialect = Objects.requireNonNull(dialect, "dialect");
            return this;
        }

        // 元数据注册表：从 @Table/@Column/@Id 中解析表和列。
        public Builder metaRegistry(EntityMetaRegistry metaRegistry) {
            this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
            return this;
        }

        // 模板解析：用于 @table(OrderRecord) 这类短名的解析与映射。
        public Builder entityNameResolver(EntityNameResolver entityNameResolver) {
            this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
            return this;
        }

        public Builder renderer(SqlRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        public Builder observers(List<DbObserver> observers) {
            this.observers = List.copyOf(Objects.requireNonNull(observers, "observers"));
            return this;
        }

        public Lumen build() {
            SqlRenderer finalRenderer = renderer == null ? new SqlRenderer(dialect) : renderer;
            Db finalDb = db;
            if (finalDb == null) {
                Objects.requireNonNull(dataSource, "dataSource");
                JdbcExecutor executor = new JdbcExecutor(dataSource);
                finalDb = new DefaultDb(
                    executor,
                    finalRenderer,
                    dialect,
                    metaRegistry,
                    entityNameResolver,
                    observers
                );
            }
            Dsl dsl = new Dsl(metaRegistry);
            return new Lumen(finalDb, dsl, dialect, metaRegistry, entityNameResolver, finalRenderer);
        }
    }
}

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
import io.lighting.lumen.sql.dialect.DialectResolver;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
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
            try {
                Constructor<?> ctor = implClass.getConstructor(
                    Db.class,
                    Dialect.class,
                    EntityMetaRegistry.class,
                    EntityNameResolver.class,
                    SqlRenderer.class
                );
                return ctor.newInstance(db, dialect, metaRegistry, entityNameResolver, renderer);
            } catch (NoSuchMethodException ex) {
                Constructor<?> ctor = implClass.getConstructor(
                    Db.class,
                    Dialect.class,
                    EntityMetaRegistry.class,
                    EntityNameResolver.class
                );
                return ctor.newInstance(db, dialect, metaRegistry, entityNameResolver);
            }
        } catch (ClassNotFoundException ex) {
            // Fallback to runtime SQL template proxy when no APT-generated implementation exists.
            return io.lighting.lumen.template.SqlTemplateProxyFactory.create(
                daoType,
                db,
                dialect,
                metaRegistry,
                entityNameResolver,
                renderer
            );
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create DAO implementation: " + implName, ex);
        }
    }

    public static final class Builder {
        private DataSource dataSource;
        private Db db;
        private Dialect dialect;
        private EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        private EntityNameResolver entityNameResolver;
        private final Map<String, Class<?>> entityNameMappings = new LinkedHashMap<>();
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

        // 方言：默认按 DataSource 自动识别，也可在这里显式覆盖。
        public Builder dialect(Dialect dialect) {
            this.dialect = Objects.requireNonNull(dialect, "dialect");
            return this;
        }

        // 元数据注册表：从 @Table/@Column/@Id 中解析表和列。
        public Builder metaRegistry(EntityMetaRegistry metaRegistry) {
            this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
            return this;
        }

        // 模板解析：默认扫描所有 @Table 实体用于短名映射。
        public Builder entityNameMappings(Map<String, Class<?>> mappings) {
            Objects.requireNonNull(mappings, "mappings");
            for (Map.Entry<String, Class<?>> entry : mappings.entrySet()) {
                addEntityNameMapping(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder addEntityNameMapping(String name, Class<?> type) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            entityNameMappings.put(name, type);
            return this;
        }

        // 模板解析：高级用法，允许自定义解析策略（默认仍会提供 @Table 扫描作为兜底）。
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
            Dialect resolvedDialect = resolveDialect();
            EntityNameResolver resolvedEntityNameResolver = resolveEntityNameResolver();
            SqlRenderer finalRenderer = renderer == null ? new SqlRenderer(resolvedDialect) : renderer;
            Db finalDb = db;
            if (finalDb == null) {
                Objects.requireNonNull(dataSource, "dataSource");
                JdbcExecutor executor = new JdbcExecutor(dataSource);
                finalDb = new DefaultDb(
                    executor,
                    finalRenderer,
                    resolvedDialect,
                    metaRegistry,
                    resolvedEntityNameResolver,
                    observers
                );
            }
            Dsl dsl = new Dsl(metaRegistry);
            return new Lumen(finalDb, dsl, resolvedDialect, metaRegistry, resolvedEntityNameResolver, finalRenderer);
        }

        private Dialect resolveDialect() {
            if (dialect != null) {
                return dialect;
            }
            if (dataSource != null) {
                return DialectResolver.resolve(dataSource);
            }
            return new LimitOffsetDialect("ansi", "\"");
        }

        private EntityNameResolver resolveEntityNameResolver() {
            EntityNameResolver autoResolver = EntityNameResolvers.auto();
            if (!entityNameMappings.isEmpty()) {
                autoResolver = EntityNameResolvers.withFallback(EntityNameResolvers.from(entityNameMappings), autoResolver);
            }
            if (entityNameResolver == null) {
                return autoResolver;
            }
            return EntityNameResolvers.withFallback(entityNameResolver, autoResolver);
        }
    }
}

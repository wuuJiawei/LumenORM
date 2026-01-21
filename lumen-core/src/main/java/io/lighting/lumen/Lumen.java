package io.lighting.lumen;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.DbObserver;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.meta.EntityMetaRegistry;
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

/**
 * Lumen ORM 入口类。
 * <p>
 * 提供 DAO 获取、DSL 查询构建等核心功能。
 * <p>
 * 使用示例:
 * <pre>{@code
 * Lumen lumen = Lumen.builder()
 *     .dataSource(dataSource)
 *     .build();
 *
 * UserDao dao = lumen.dao(UserDao.class);
 * User user = dao.findById(1L);
 * }</pre>
 */
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

    /**
     * 获取 Builder 实例。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取 DAO 实例。
     * <p>
     * 加载 APT 生成的 *_Impl 类，如果不存在则抛出异常。
     *
     * @param daoType DAO 接口类型
     * @param <T> DAO 类型
     * @return DAO 实例
     * @throws IllegalStateException 如果 APT 未生成实现类
     */
    public <T> T dao(Class<T> daoType) {
        Objects.requireNonNull(daoType, "daoType");
        if (!daoType.isInterface()) {
            throw new IllegalArgumentException("DAO type must be an interface: " + daoType.getName());
        }
        Object instance = daoCache.computeIfAbsent(daoType, this::newDaoInstance);
        return daoType.cast(instance);
    }

    /**
     * 创建 DAO 实例。
     * <p>
     * 直接加载 APT 生成的 *_Impl 类，不存在 fallback 机制。
     *
     * @param daoType DAO 接口类型
     * @return DAO 实例
     */
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
            throw new IllegalStateException(
                """
                APT-generated implementation not found: %s

                Please ensure annotation processing is enabled:
                - Maven: Runs automatically (no extra config needed)
                - IDE: Enable annotation processing in settings

                Generated files location: target/generated-sources/annotations
                """.formatted(implName), ex
            );
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create DAO implementation: " + implName, ex);
        }
    }

    /**
     * 获取 DSL 查询构建器。
     */
    public Dsl dsl() {
        return dsl;
    }

    /**
     * 获取数据库操作接口。
     */
    public Db db() {
        return db;
    }

    /**
     * 获取 SQL 方言。
     */
    public Dialect dialect() {
        return dialect;
    }

    /**
     * 获取实体元数据注册表。
     */
    public EntityMetaRegistry metaRegistry() {
        return metaRegistry;
    }

    /**
     * 获取实体名称解析器。
     */
    public EntityNameResolver entityNameResolver() {
        return entityNameResolver;
    }

    /**
     * 获取 SQL 渲染器。
     */
    public SqlRenderer renderer() {
        return renderer;
    }

    /**
     * Lumen 构建器。
     */
    public static final class Builder {
        private DataSource dataSource;
        private Db db;
        private Dialect dialect;
        private EntityMetaRegistry metaRegistry;
        private EntityNameResolver entityNameResolver;
        private final Map<String, Class<?>> entityNameMappings = new LinkedHashMap<>();
        private SqlRenderer renderer;
        private List<DbObserver> observers = List.of();
        private boolean startupLogEnabled = true;

        private Builder() {
        }

        /**
         * 设置数据源。
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
            return this;
        }

        /**
         * 设置自定义 Db 实现。
         */
        public Builder db(Db db) {
            this.db = Objects.requireNonNull(db, "db");
            return this;
        }

        /**
         * 设置 SQL 方言。默认按 DataSource 自动识别。
         */
        public Builder dialect(Dialect dialect) {
            this.dialect = Objects.requireNonNull(dialect, "dialect");
            return this;
        }

        /**
         * 设置实体元数据注册表。
         */
        public Builder metaRegistry(EntityMetaRegistry metaRegistry) {
            this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
            return this;
        }

        /**
         * 设置实体名称映射。
         */
        public Builder entityNameMappings(Map<String, Class<?>> mappings) {
            Objects.requireNonNull(mappings, "mappings");
            for (Map.Entry<String, Class<?>> entry : mappings.entrySet()) {
                addEntityNameMapping(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * 添加实体名称映射。
         */
        public Builder addEntityNameMapping(String name, Class<?> type) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            entityNameMappings.put(name, type);
            return this;
        }

        /**
         * 设置实体名称解析器。
         */
        public Builder entityNameResolver(EntityNameResolver entityNameResolver) {
            this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
            return this;
        }

        /**
         * 设置 SQL 渲染器。
         */
        public Builder renderer(SqlRenderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        /**
         * 设置数据库观察者（用于可观测性）。
         */
        public Builder observers(List<DbObserver> observers) {
            this.observers = List.copyOf(Objects.requireNonNull(observers, "observers"));
            return this;
        }

        /**
         * 启用/禁用启动日志。
         */
        public Builder startupLogEnabled(boolean enabled) {
            this.startupLogEnabled = enabled;
            return this;
        }

        /**
         * 构建 Lumen 实例。
         */
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
            Lumen lumen = new Lumen(finalDb, dsl, resolvedDialect, metaRegistry, resolvedEntityNameResolver, finalRenderer);
            if (startupLogEnabled) {
                logStartup(lumen);
            }
            return lumen;
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

    private static void logStartup(Lumen lumen) {
        String message = "Lumen started (dialect=" + lumen.dialect().id() + ", db=" + lumen.db().getClass().getSimpleName() + ")";
        if (trySlf4j(Lumen.class, message)) {
            return;
        }
        System.out.println(message);
    }

    private static boolean trySlf4j(Class<?> type, String message) {
        try {
            Class<?> factory = Class.forName("org.slf4j.LoggerFactory");
            Object logger = factory.getMethod("getLogger", Class.class).invoke(null, type);
            logger.getClass().getMethod("info", String.class).invoke(logger, message);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}

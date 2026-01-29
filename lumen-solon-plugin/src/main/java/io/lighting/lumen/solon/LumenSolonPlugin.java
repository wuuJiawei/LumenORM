package io.lighting.lumen.solon;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.DbObserver;
import io.lighting.lumen.db.SqlLog;
import io.lighting.lumen.meta.EntityMeta;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * LumenORM auto-configuration for Solon framework.
 * <p>
 * This class provides @Bean definitions for LumenORM components.
 * Solon will automatically discover and load this configuration.
 * <p>
 * Usage:
 * <pre>{@code
 * // Add plugin to Solon app
 * Solon.start(App.class, args);
 *
 * // Inject Lumen or Db where needed
 * @Inject
 * Lumen lumen;
 * }</pre>
 */
public class LumenSolonPlugin {

    /**
     * Configuration class for LumenORM beans.
     */
    @Configuration
    public static class LumenConfiguration {

        @Bean
        public EntityNameResolver entityNameResolver() {
            return EntityNameResolvers.auto();
        }

        @Bean
        public EntityMetaRegistry entityMetaRegistry() {
            return new ReflectionEntityMetaRegistry();
        }

        @Bean
        public Dialect dialect(@Inject DataSource dataSource) {
            return io.lighting.lumen.sql.dialect.DialectResolver.resolve(dataSource);
        }

        @Bean
        public SqlRenderer sqlRenderer(Dialect dialect) {
            return new SqlRenderer(dialect);
        }

        @Bean
        public List<DbObserver> dbObservers() {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(LumenSolonPlugin.class.getName());
            SqlLog sqlLog = SqlLog.builder()
                .mode(SqlLog.Mode.SEPARATE)
                .logOnExecute(true)
                .includeOperation(true)
                .prefix("SQL:")
                .sink(logger::info)
                .build();

            List<DbObserver> observers = new ArrayList<>();
            observers.add(sqlLog);
            return observers;
        }

        @Bean
        public Lumen lumen(
            @Inject DataSource dataSource,
            Dialect dialect,
            EntityMetaRegistry metaRegistry,
            EntityNameResolver entityNameResolver,
            SqlRenderer renderer,
            List<DbObserver> observers
        ) {
            return Lumen.builder()
                .dataSource(dataSource)
                .dialect(dialect)
                .metaRegistry(metaRegistry)
                .entityNameResolver(entityNameResolver)
                .renderer(renderer)
                .observers(observers)
                .build();
        }

        @Bean
        public Db db(Lumen lumen) {
            return lumen.db();
        }
    }

    /**
     * Simple EntityMetaRegistry implementation using reflection.
     * Production code should use APT-generated entity metadata classes.
     */
    private static final class ReflectionEntityMetaRegistry implements EntityMetaRegistry {
        private final java.util.concurrent.ConcurrentMap<Class<?>, EntityMeta> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public EntityMeta metaOf(Class<?> entityType) {
            if (entityType == null) {
                throw new IllegalArgumentException("entityType must not be null");
            }
            return cache.computeIfAbsent(entityType, this::buildMeta);
        }

        private EntityMeta buildMeta(Class<?> entityType) {
            io.lighting.lumen.meta.Table table = entityType.getAnnotation(io.lighting.lumen.meta.Table.class);
            if (table == null) {
                throw new IllegalArgumentException("Missing @Table on " + entityType.getName());
            }
            if (table.name().isBlank()) {
                throw new IllegalArgumentException("Table name must not be blank: " + entityType.getName());
            }

            java.util.Map<String, String> fieldToColumn = new java.util.LinkedHashMap<>();
            java.util.Set<String> columns = new java.util.LinkedHashSet<>();
            io.lighting.lumen.meta.IdMeta idMeta = null;
            io.lighting.lumen.meta.LogicDeleteMeta logicDeleteMeta = null;

            for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || java.lang.reflect.Modifier.isTransient(field.getModifiers())
                    ) {
                        continue;
                    }

                    io.lighting.lumen.meta.Column column = field.getAnnotation(io.lighting.lumen.meta.Column.class);
                    io.lighting.lumen.meta.Id id = field.getAnnotation(io.lighting.lumen.meta.Id.class);
                    io.lighting.lumen.meta.LogicDelete logicDelete = field.getAnnotation(io.lighting.lumen.meta.LogicDelete.class);

                    if (column == null && id == null && logicDelete == null) {
                        continue;
                    }

                    String columnName = column != null ? column.name() : field.getName();
                    if (columnName.isBlank()) {
                        throw new IllegalArgumentException(
                            "Column name must not be blank: " + entityType.getName() + "." + field.getName()
                        );
                    }

                    String fieldName = field.getName();
                    if (!columns.add(columnName)) {
                        throw new IllegalArgumentException("Duplicate column mapping: " + columnName);
                    }
                    fieldToColumn.put(fieldName, columnName);

                    if (id != null) {
                        if (idMeta != null) {
                            throw new IllegalArgumentException("Duplicate @Id on " + entityType.getName());
                        }
                        idMeta = new io.lighting.lumen.meta.IdMeta(fieldName, columnName, id.strategy());
                    }

                    if (logicDelete != null) {
                        if (logicDeleteMeta != null) {
                            throw new IllegalArgumentException("Duplicate @LogicDelete on " + entityType.getName());
                        }
                        Object active = parseLogicValue(logicDelete.active(), field, "active");
                        Object deleted = parseLogicValue(logicDelete.deleted(), field, "deleted");
                        logicDeleteMeta = new io.lighting.lumen.meta.LogicDeleteMeta(fieldName, columnName, active, deleted);
                    }
                }
            }

            return new io.lighting.lumen.meta.EntityMeta(table.name(), fieldToColumn, idMeta, logicDeleteMeta);
        }

        private Object parseLogicValue(String raw, java.lang.reflect.Field field, String label) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("LogicDelete " + label + " value must not be blank");
            }
            Class<?> type = field.getType();
            if (type == String.class) {
                return raw;
            }
            if (type == boolean.class || type == Boolean.class) {
                String normalized = raw.trim().toLowerCase();
                if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
                    return true;
                }
                if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
                    return false;
                }
                throw new IllegalArgumentException("Invalid boolean value: " + raw);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(raw);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(raw);
            }
            throw new IllegalArgumentException("Unsupported @LogicDelete type: " + type.getName());
        }
    }
}

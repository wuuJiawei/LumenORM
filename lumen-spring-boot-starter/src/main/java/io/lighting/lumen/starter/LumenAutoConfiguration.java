package io.lighting.lumen.starter;

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
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Lumen.class)
@EnableConfigurationProperties(LumenProperties.class)
public class LumenAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LumenAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public LumenProperties lumenProperties() {
        return new LumenProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public EntityNameResolver entityNameResolver() {
        return EntityNameResolvers.auto();
    }

    @Bean
    @ConditionalOnMissingBean
    public EntityMetaRegistry entityMetaRegistry() {
        return new ReflectionEntityMetaRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public Dialect dialect(DataSource dataSource) {
        return io.lighting.lumen.sql.dialect.DialectResolver.resolve(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlRenderer sqlRenderer(Dialect dialect) {
        return new SqlRenderer(dialect);
    }

    @Bean
    @ConditionalOnMissingBean
    public List<DbObserver> dbObservers(LumenProperties properties) {
        SqlLog sqlLog = properties.getSql().build(LOGGER::info);
        if (sqlLog == null) {
            return List.of();
        }
        List<DbObserver> observers = new ArrayList<>();
        observers.add(sqlLog);
        return observers;
    }

    @Bean
    @ConditionalOnMissingBean
    public Lumen lumen(
        DataSource dataSource,
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
    @ConditionalOnBean(Lumen.class)
    @ConditionalOnMissingBean
    public Db db(Lumen lumen) {
        return lumen.db();
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

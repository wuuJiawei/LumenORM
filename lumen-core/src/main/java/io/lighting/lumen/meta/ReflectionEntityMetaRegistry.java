package io.lighting.lumen.meta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple EntityMetaRegistry implementation using reflection.
 * Production code should use APT-generated entity metadata classes.
 */
public class ReflectionEntityMetaRegistry implements EntityMetaRegistry {
    private final ConcurrentMap<Class<?>, EntityMeta> cache = new ConcurrentHashMap<>();

    @Override
    public EntityMeta metaOf(Class<?> entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        return cache.computeIfAbsent(entityType, this::buildMeta);
    }

    private EntityMeta buildMeta(Class<?> entityType) {
        Table table = entityType.getAnnotation(Table.class);
        if (table == null) {
            throw new IllegalArgumentException("Missing @Table on " + entityType.getName());
        }
        if (table.name().isBlank()) {
            throw new IllegalArgumentException("Table name must not be blank: " + entityType.getName());
        }

        Map<String, String> fieldToColumn = new LinkedHashMap<>();
        Set<String> columns = new LinkedHashSet<>();
        IdMeta idMeta = null;
        LogicDeleteMeta logicDeleteMeta = null;

        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                    || Modifier.isTransient(field.getModifiers())
                ) {
                    continue;
                }

                Column column = field.getAnnotation(Column.class);
                Id id = field.getAnnotation(Id.class);
                LogicDelete logicDelete = field.getAnnotation(LogicDelete.class);

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
                        throw new IllegalArgumentException("Multiple @Id fields in " + entityType.getName());
                    }
                    idMeta = new IdMeta(fieldName, columnName, IdStrategy.AUTO);
                }

                if (logicDelete != null) {
                    if (logicDeleteMeta != null) {
                        throw new IllegalArgumentException("Multiple @LogicDelete fields in " + entityType.getName());
                    }
                    logicDeleteMeta = new LogicDeleteMeta(fieldName, columnName, logicDelete.active(), logicDelete.deleted());
                }
            }
        }

        if (idMeta == null) {
            throw new IllegalArgumentException("Missing @Id in " + entityType.getName());
        }

        return new EntityMeta(table.name(), fieldToColumn, idMeta, logicDeleteMeta);
    }
}

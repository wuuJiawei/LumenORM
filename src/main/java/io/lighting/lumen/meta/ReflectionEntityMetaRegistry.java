package io.lighting.lumen.meta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReflectionEntityMetaRegistry implements EntityMetaRegistry {
    private final ConcurrentMap<Class<?>, EntityMeta> cache = new ConcurrentHashMap<>();

    @Override
    public EntityMeta metaOf(Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType");
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
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                Id id = field.getAnnotation(Id.class);
                if (column == null && id == null) {
                    continue;
                }
                String columnName = column != null ? column.name() : field.getName();
                if (columnName.isBlank()) {
                    throw new IllegalArgumentException(
                        "Column name must not be blank: " + entityType.getName() + "." + field.getName()
                    );
                }
                String fieldName = field.getName();
                if (fieldToColumn.containsKey(fieldName)) {
                    throw new IllegalArgumentException("Duplicate field mapping: " + fieldName);
                }
                if (!columns.add(columnName)) {
                    throw new IllegalArgumentException("Duplicate column mapping: " + columnName);
                }
                fieldToColumn.put(fieldName, columnName);
            }
        }
        return new EntityMeta(table.name(), fieldToColumn);
    }
}

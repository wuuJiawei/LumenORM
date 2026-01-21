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
 * 测试用的实体元数据注册表。
 * <p>
 * 注意：此类仅用于测试。生产代码应使用 APT 生成的 UserMeta 类。
 */
public final class TestEntityMetaRegistry implements EntityMetaRegistry {
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
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
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
                if (fieldToColumn.containsKey(fieldName)) {
                    throw new IllegalArgumentException("Duplicate field mapping: " + fieldName);
                }
                if (!columns.add(columnName)) {
                    throw new IllegalArgumentException("Duplicate column mapping: " + columnName);
                }
                fieldToColumn.put(fieldName, columnName);
                if (id != null) {
                    if (idMeta != null) {
                        throw new IllegalArgumentException("Duplicate @Id on " + entityType.getName());
                    }
                    idMeta = new IdMeta(fieldName, columnName, id.strategy());
                }
                if (logicDelete != null) {
                    if (logicDeleteMeta != null) {
                        throw new IllegalArgumentException("Duplicate @LogicDelete on " + entityType.getName());
                    }
                    Object active = parseLogicValue(logicDelete.active(), field, "active");
                    Object deleted = parseLogicValue(logicDelete.deleted(), field, "deleted");
                    logicDeleteMeta = new LogicDeleteMeta(fieldName, columnName, active, deleted);
                }
            }
        }
        return new EntityMeta(table.name(), fieldToColumn, idMeta, logicDeleteMeta);
    }

    private Object parseLogicValue(String raw, Field field, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LogicDelete " + label + " value must not be blank");
        }
        Class<?> type = field.getType();
        if (type == String.class) {
            return raw;
        }
        if (type == boolean.class || type == Boolean.class) {
            return parseBoolean(raw, field, label);
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return parseNumber(raw, type, field, label);
        }
        if (type.isEnum()) {
            return parseEnum(raw, type, field, label);
        }
        throw new IllegalArgumentException(
            "Unsupported @LogicDelete type: " + type.getName() + " on " + field.getDeclaringClass().getName()
        );
    }

    private Object parseBoolean(String raw, Field field, String label) {
        String normalized = raw.trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException(
            "Invalid @LogicDelete " + label + " value on " + field.getDeclaringClass().getName() + "." + field.getName()
        );
    }

    private Object parseNumber(String raw, Class<?> type, Field field, String label) {
        try {
            if (type == byte.class || type == Byte.class) {
                return Byte.parseByte(raw);
            }
            if (type == short.class || type == Short.class) {
                return Short.parseShort(raw);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(raw);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(raw);
            }
            if (type == float.class || type == Float.class) {
                return Float.parseFloat(raw);
            }
            if (type == double.class || type == Double.class) {
                return Double.parseDouble(raw);
            }
            if (type == java.math.BigInteger.class) {
                return new java.math.BigInteger(raw);
            }
            if (type == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(raw);
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "Invalid @LogicDelete " + label + " value on " + field.getDeclaringClass().getName() + "." + field.getName(),
                ex
            );
        }
        throw new IllegalArgumentException(
            "Unsupported @LogicDelete type: " + type.getName() + " on " + field.getDeclaringClass().getName()
        );
    }

    private Object parseEnum(String raw, Class<?> type, Field field, String label) {
        Object[] constants = type.getEnumConstants();
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(raw)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
            "Invalid @LogicDelete " + label + " value on " + field.getDeclaringClass().getName() + "." + field.getName()
        );
    }
}

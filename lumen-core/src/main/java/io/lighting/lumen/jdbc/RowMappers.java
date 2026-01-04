package io.lighting.lumen.jdbc;

import io.lighting.lumen.meta.Column;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RowMappers {
    private static final Map<Class<?>, RowMapper<?>> CACHE = new ConcurrentHashMap<>();

    private RowMappers() {
    }

    public static <T> RowMapper<T> auto(Class<T> type) {
        Objects.requireNonNull(type, "type");
        @SuppressWarnings("unchecked")
        RowMapper<T> mapper = (RowMapper<T>) CACHE.computeIfAbsent(type, RowMappers::createMapper);
        return mapper;
    }

    private static RowMapper<?> createMapper(Class<?> type) {
        if (type.isRecord()) {
            return recordMapper(type);
        }
        return beanMapper(type);
    }

    private static <T> RowMapper<T> recordMapper(Class<T> type) {
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) {
            return beanMapper(type);
        }
        ColumnSpec[] specs = new ColumnSpec[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            paramTypes[i] = component.getType();
            String name = component.getName();
            Field field = findField(type, name);
            if (field != null) {
                Column column = field.getAnnotation(Column.class);
                if (column != null && !column.name().isBlank()) {
                    name = column.name();
                }
            }
            specs[i] = new ColumnSpec(name, component.getGenericType());
        }
        Constructor<T> ctor = constructor(type, paramTypes);
        return resultSet -> {
            Map<String, Integer> indexes = columnIndexes(resultSet);
            Object[] args = new Object[specs.length];
            for (int i = 0; i < specs.length; i++) {
                ColumnSpec spec = specs[i];
                Integer index = indexes.get(normalize(spec.columnName));
                Object value = null;
                if (index != null) {
                    value = JdbcTypeAdapters.read(resultSet, index, spec.type);
                }
                args[i] = defaultIfPrimitive(spec.type, value);
            }
            try {
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed to map row to " + type.getSimpleName(), ex);
            }
        };
    }

    private static <T> RowMapper<T> beanMapper(Class<T> type) {
        Constructor<T> ctor = constructor(type);
        Map<String, Field> fields = fieldMap(type);
        return resultSet -> {
            Map<String, Integer> indexes = columnIndexes(resultSet);
            T instance;
            try {
                instance = ctor.newInstance();
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Failed to map row to " + type.getSimpleName(), ex);
            }
            for (Map.Entry<String, Field> entry : fields.entrySet()) {
                Integer index = indexes.get(entry.getKey());
                if (index == null) {
                    continue;
                }
                Field field = entry.getValue();
                Object value = JdbcTypeAdapters.read(resultSet, index, field.getGenericType());
                if (value == null && field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.set(instance, value);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Failed to map row to " + type.getSimpleName(), ex);
                }
            }
            return instance;
        };
    }

    private static Map<String, Integer> columnIndexes(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            indexes.putIfAbsent(normalize(label), i);
        }
        return indexes;
    }

    private static Map<String, Field> fieldMap(Class<?> type) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                Column column = field.getAnnotation(Column.class);
                if (column != null && !column.name().isBlank()) {
                    name = column.name();
                }
                field.setAccessible(true);
                fields.putIfAbsent(normalize(name), field);
            }
        }
        return fields;
    }

    private static Object defaultIfPrimitive(Type type, Object value) {
        Class<?> raw = JdbcTypeAdapters.rawClass(type);
        if (value != null || raw == null || !raw.isPrimitive()) {
            return value;
        }
        if (raw == boolean.class) {
            return false;
        }
        if (raw == char.class) {
            return '\0';
        }
        if (raw == byte.class) {
            return (byte) 0;
        }
        if (raw == short.class) {
            return (short) 0;
        }
        if (raw == int.class) {
            return 0;
        }
        if (raw == long.class) {
            return 0L;
        }
        if (raw == float.class) {
            return 0f;
        }
        if (raw == double.class) {
            return 0d;
        }
        return null;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static <T> Constructor<T> constructor(Class<T> type, Class<?>... parameterTypes) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor(parameterTypes);
            ctor.setAccessible(true);
            return ctor;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("No accessible constructor for " + type.getSimpleName(), ex);
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // try next superclass
            }
        }
        return null;
    }

    private record ColumnSpec(String columnName, Type type) {
    }
}

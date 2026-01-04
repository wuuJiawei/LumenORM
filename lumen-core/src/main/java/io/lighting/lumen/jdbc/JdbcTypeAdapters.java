package io.lighting.lumen.jdbc;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JdbcTypeAdapters {
    private static final Map<Type, JdbcTypeAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    static {
        register(List.class, new ListTypeAdapter());
        register(Set.class, new SetTypeAdapter());
        register(Map.class, new MapTypeAdapter());
    }

    private JdbcTypeAdapters() {
    }

    public static <T> void register(Class<T> type, JdbcTypeAdapter<? extends T> adapter) {
        register((Type) type, adapter);
    }

    public static <T> void register(TypeRef<T> typeRef, JdbcTypeAdapter<? extends T> adapter) {
        Objects.requireNonNull(typeRef, "typeRef");
        register(typeRef.type(), adapter);
    }

    public static void register(Type type, JdbcTypeAdapter<?> adapter) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(adapter, "adapter");
        ADAPTERS.put(type, adapter);
    }

    public static Object read(ResultSet resultSet, int index, Type targetType) throws SQLException {
        Objects.requireNonNull(resultSet, "resultSet");
        Objects.requireNonNull(targetType, "targetType");
        JdbcTypeAdapter<?> adapter = findAdapter(targetType);
        if (adapter != null) {
            return adapter.read(resultSet, index, targetType);
        }
        return simpleRead(resultSet, index, targetType);
    }

    public static Object convertValue(Object value, Type targetType) {
        Objects.requireNonNull(targetType, "targetType");
        JdbcTypeAdapter<?> adapter = findAdapter(targetType);
        if (adapter != null) {
            return adapter.convert(value, targetType);
        }
        return simpleConvert(value, targetType);
    }

    static Object simpleRead(ResultSet resultSet, int index, Type targetType) throws SQLException {
        Class<?> raw = rawClass(targetType);
        Object value;
        if (raw != null && raw != Object.class) {
            Class<?> boxed = boxType(raw);
            try {
                value = resultSet.getObject(index, boxed);
            } catch (SQLException ex) {
                value = resultSet.getObject(index);
            }
        } else {
            value = resultSet.getObject(index);
        }
        return simpleConvert(value, targetType);
    }

    static Object simpleConvert(Object value, Type targetType) {
        if (value == null) {
            return null;
        }
        Class<?> raw = rawClass(targetType);
        if (raw == null || raw == Object.class) {
            return value;
        }
        if (raw.isInstance(value)) {
            return value;
        }
        if (raw.isEnum()) {
            return convertEnum(raw, value);
        }
        if (raw == String.class) {
            return value.toString();
        }
        if (raw == UUID.class) {
            return convertUuid(value);
        }
        if (raw == java.time.LocalDate.class) {
            return convertLocalDate(value);
        }
        if (raw == java.time.LocalDateTime.class) {
            return convertLocalDateTime(value);
        }
        if (raw == java.time.LocalTime.class) {
            return convertLocalTime(value);
        }
        if (raw.isArray()) {
            return convertArray(value, raw.getComponentType());
        }
        if (Number.class.isAssignableFrom(boxType(raw))) {
            return convertNumber(value, boxType(raw));
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return convertBoolean(value);
        }
        if (raw == Character.class || raw == char.class) {
            return convertChar(value);
        }
        return value;
    }

    private static JdbcTypeAdapter<?> findAdapter(Type targetType) {
        JdbcTypeAdapter<?> adapter = ADAPTERS.get(targetType);
        if (adapter != null) {
            return adapter;
        }
        if (targetType instanceof ParameterizedType parameterized) {
            adapter = ADAPTERS.get(parameterized.getRawType());
            if (adapter != null) {
                return adapter;
            }
        }
        if (targetType instanceof Class<?> clazz) {
            Class<?> boxed = boxType(clazz);
            if (boxed != clazz) {
                adapter = ADAPTERS.get(boxed);
                if (adapter != null) {
                    return adapter;
                }
            }
        }
        return null;
    }

    static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> clazz) {
                return clazz;
            }
        }
        if (type instanceof WildcardType wildcard) {
            return rawClass(resolveType(wildcard));
        }
        if (type instanceof TypeVariable<?> variable) {
            return rawClass(resolveType(variable));
        }
        return null;
    }

    private static Type resolveType(WildcardType wildcard) {
        Type[] upper = wildcard.getUpperBounds();
        if (upper.length > 0) {
            return upper[0];
        }
        Type[] lower = wildcard.getLowerBounds();
        if (lower.length > 0) {
            return lower[0];
        }
        return Object.class;
    }

    private static Type resolveType(TypeVariable<?> variable) {
        Type[] bounds = variable.getBounds();
        if (bounds.length > 0) {
            return bounds[0];
        }
        return Object.class;
    }

    private static Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
    }

    private static Object convertEnum(Class<?> enumType, Object value) {
        Object[] constants = enumType.getEnumConstants();
        if (value instanceof Number number) {
            int ordinal = number.intValue();
            if (ordinal >= 0 && ordinal < constants.length) {
                return constants[ordinal];
            }
        }
        String name = value.toString();
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                return constant;
            }
        }
        return constants.length > 0 ? constants[0] : null;
    }

    private static Object convertUuid(Object value) {
        if (value instanceof byte[] bytes) {
            if (bytes.length == 16) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                long high = buffer.getLong();
                long low = buffer.getLong();
                return new UUID(high, low);
            }
        }
        return UUID.fromString(value.toString());
    }

    private static Object convertLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return java.time.LocalDate.parse(value.toString());
    }

    private static Object convertLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return java.time.LocalDateTime.parse(value.toString());
    }

    private static Object convertLocalTime(Object value) {
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        return java.time.LocalTime.parse(value.toString());
    }

    private static Object convertArray(Object value, Class<?> componentType) {
        Object raw = value;
        if (value instanceof java.sql.Array sqlArray) {
            try {
                raw = sqlArray.getArray();
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Failed to read SQL array", ex);
            }
        }
        if (raw == null) {
            return null;
        }
        int length;
        if (raw.getClass().isArray()) {
            length = Array.getLength(raw);
            Object array = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(raw, i);
                Array.set(array, i, convertValue(element, componentType));
            }
            return array;
        }
        if (raw instanceof Collection<?> collection) {
            length = collection.size();
            Object array = Array.newInstance(componentType, length);
            int index = 0;
            for (Object element : collection) {
                Array.set(array, index++, convertValue(element, componentType));
            }
            return array;
        }
        Object array = Array.newInstance(componentType, 1);
        Array.set(array, 0, convertValue(raw, componentType));
        return array;
    }

    private static Object convertNumber(Object value, Class<?> targetType) {
        if (value instanceof Number number) {
            return narrowNumber(number, targetType);
        }
        if (value instanceof Boolean booleanValue) {
            return narrowNumber(booleanValue ? 1 : 0, targetType);
        }
        String text = value.toString();
        if (targetType == BigDecimal.class) {
            return new BigDecimal(text);
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(text);
        }
        BigDecimal decimal = new BigDecimal(text);
        return narrowNumber(decimal, targetType);
    }

    private static Object narrowNumber(Number number, Class<?> targetType) {
        if (targetType == Integer.class) {
            return number.intValue();
        }
        if (targetType == Long.class) {
            return number.longValue();
        }
        if (targetType == Short.class) {
            return number.shortValue();
        }
        if (targetType == Byte.class) {
            return number.byteValue();
        }
        if (targetType == Double.class) {
            return number.doubleValue();
        }
        if (targetType == Float.class) {
            return number.floatValue();
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(number.toString());
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(number.toString());
        }
        return number;
    }

    private static Object convertBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return Boolean.parseBoolean(text);
    }

    private static Object convertChar(Object value) {
        if (value instanceof Character character) {
            return character;
        }
        if (value instanceof Number number) {
            return (char) number.intValue();
        }
        String text = value.toString();
        return text.isEmpty() ? '\0' : text.charAt(0);
    }

    private static final class ListTypeAdapter implements JdbcTypeAdapter<Object> {
        @Override
        public Object read(ResultSet resultSet, int index, Type targetType) throws SQLException {
            Object value = resultSet.getObject(index);
            return toList(value, targetType);
        }

        @Override
        public Object convert(Object value, Type targetType) {
            return toList(value, targetType);
        }

        private List<Object> toList(Object value, Type targetType) {
            if (value == null) {
                return null;
            }
            Type elementType = extractElementType(targetType);
            Collection<?> collection = toCollection(value);
            List<Object> results = new ArrayList<>(collection.size());
            for (Object element : collection) {
                results.add(convertValue(element, elementType));
            }
            return results;
        }
    }

    private static final class SetTypeAdapter implements JdbcTypeAdapter<Object> {
        @Override
        public Object read(ResultSet resultSet, int index, Type targetType) throws SQLException {
            Object value = resultSet.getObject(index);
            return toSet(value, targetType);
        }

        @Override
        public Object convert(Object value, Type targetType) {
            return toSet(value, targetType);
        }

        private Set<Object> toSet(Object value, Type targetType) {
            if (value == null) {
                return null;
            }
            Type elementType = extractElementType(targetType);
            Collection<?> collection = toCollection(value);
            Set<Object> results = new java.util.LinkedHashSet<>(collection.size());
            for (Object element : collection) {
                results.add(convertValue(element, elementType));
            }
            return results;
        }
    }

    private static final class MapTypeAdapter implements JdbcTypeAdapter<Object> {
        @Override
        public Object read(ResultSet resultSet, int index, Type targetType) throws SQLException {
            Object value = resultSet.getObject(index);
            return toMap(value, targetType);
        }

        @Override
        public Object convert(Object value, Type targetType) {
            return toMap(value, targetType);
        }

        private Map<Object, Object> toMap(Object value, Type targetType) {
            if (value == null) {
                return null;
            }
            if (!(value instanceof Map<?, ?> input)) {
                throw new IllegalArgumentException("Map value must be a java.util.Map but got " + value.getClass());
            }
            Type[] keyValueTypes = extractMapTypes(targetType);
            Map<Object, Object> results = new LinkedHashMap<>(input.size());
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                Object key = convertValue(entry.getKey(), keyValueTypes[0]);
                Object val = convertValue(entry.getValue(), keyValueTypes[1]);
                results.put(key, val);
            }
            return results;
        }
    }

    private static Collection<?> toCollection(Object value) {
        Object raw = value;
        if (value instanceof java.sql.Array sqlArray) {
            try {
                raw = sqlArray.getArray();
            } catch (SQLException ex) {
                throw new IllegalArgumentException("Failed to read SQL array", ex);
            }
        }
        if (raw instanceof Collection<?> collection) {
            return collection;
        }
        if (raw != null && raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(raw, i));
            }
            return list;
        }
        return List.of(raw);
    }

    private static Type extractElementType(Type targetType) {
        if (targetType instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 1) {
                return resolveGeneric(args[0]);
            }
        }
        return Object.class;
    }

    private static Type[] extractMapTypes(Type targetType) {
        if (targetType instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length == 2) {
                return new Type[] { resolveGeneric(args[0]), resolveGeneric(args[1]) };
            }
        }
        return new Type[] { Object.class, Object.class };
    }

    private static Type resolveGeneric(Type type) {
        if (type instanceof WildcardType wildcard) {
            return resolveType(wildcard);
        }
        if (type instanceof TypeVariable<?> variable) {
            return resolveType(variable);
        }
        return type;
    }
}

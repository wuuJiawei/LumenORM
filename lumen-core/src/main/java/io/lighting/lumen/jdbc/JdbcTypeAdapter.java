package io.lighting.lumen.jdbc;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface JdbcTypeAdapter<T> {
    T read(ResultSet resultSet, int index, Type targetType) throws SQLException;

    default T convert(Object value, Type targetType) {
        @SuppressWarnings("unchecked")
        T converted = (T) JdbcTypeAdapters.simpleConvert(value, targetType);
        return converted;
    }
}

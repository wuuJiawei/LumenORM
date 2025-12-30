package io.lighting.lumen.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface GeneratedKeyMapper<T> {
    T map(ResultSet resultSet) throws SQLException;
}

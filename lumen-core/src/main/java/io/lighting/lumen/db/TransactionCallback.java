package io.lighting.lumen.db;

import java.sql.SQLException;

@FunctionalInterface
public interface TransactionCallback<T> {
    T apply(Db db) throws SQLException;
}

package io.lighting.lumen.active;

import io.lighting.lumen.sql.ast.Expr;
import java.sql.SQLException;
import java.util.List;

public abstract class ActiveRecord<T extends ActiveRecord<T>> extends ActiveRecordSupport<T> {
    public static void configure(ActiveRecordConfig config) {
        ActiveRecordSupport.configure(config);
    }

    public static <E> E findById(Class<E> type, Object id) throws SQLException {
        return ActiveRecordSupport.findById(type, id);
    }

    public static <E> List<E> list(Class<E> type) throws SQLException {
        return ActiveRecordSupport.list(type);
    }

    public static <E> List<E> list(Class<E> type, Expr where) throws SQLException {
        return ActiveRecordSupport.list(type, where);
    }

    public int insert() throws SQLException {
        return insertRows();
    }

    public int update() throws SQLException {
        return updateRows();
    }

    public int delete() throws SQLException {
        return deleteRows();
    }

    public int hardDelete() throws SQLException {
        return hardDeleteRows();
    }

    public int logicalDelete() throws SQLException {
        return logicalDeleteRows();
    }

    public int save() throws SQLException {
        return saveRows();
    }
}

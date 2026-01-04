package io.lighting.lumen.active;

import io.lighting.lumen.sql.ast.Expr;
import java.sql.SQLException;
import java.util.List;

public abstract class Model<T extends Model<T>> extends ActiveRecordSupport<T> {
    public static void configure(ActiveRecordConfig config) {
        ActiveRecordSupport.configure(config);
    }

    public static <T extends Model<T>> ActiveQuery<T> of(Class<T> type) {
        return ActiveQuery.of(type);
    }

    public boolean insert() throws SQLException {
        return insertRows() > 0;
    }

    public boolean updateById() throws SQLException {
        return updateRows() > 0;
    }

    public boolean deleteById() throws SQLException {
        return deleteRows() > 0;
    }

    public boolean insertOrUpdate() throws SQLException {
        return saveRows() > 0;
    }

    public T selectById(Object id) throws SQLException {
        return modelType().cast(findById(modelType(), id));
    }

    public T selectById() throws SQLException {
        ActiveRecordConfig config = requireConfig();
        Object id = requireIdValue(requireId(meta(config)));
        return selectById(id);
    }

    public List<T> selectAll() throws SQLException {
        return list(modelType());
    }

    public List<T> selectList(Expr where) throws SQLException {
        return list(modelType(), where);
    }

    @SuppressWarnings("unchecked")
    private Class<T> modelType() {
        return (Class<T>) entityType();
    }
}

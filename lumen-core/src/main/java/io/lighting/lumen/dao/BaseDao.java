package io.lighting.lumen.dao;

import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.sql.ast.Expr;
import java.sql.SQLException;
import java.util.List;

/**
 * Base CRUD DAO similar to MyBatis-Plus base interface for CRUD.
 *
 * @param <T> entity type
 */
public interface BaseDao<T> {
    default int insert(T entity) throws SQLException {
        return DaoSupport.insert(context(), entityType(), entity);
    }

    default int updateById(T entity) throws SQLException {
        return DaoSupport.updateById(context(), entityType(), entity);
    }

    default int deleteById(Object id) throws SQLException {
        return DaoSupport.deleteById(context(), entityType(), id);
    }

    default T selectById(Object id) throws SQLException {
        return DaoSupport.selectById(context(), entityType(), id);
    }

    default List<T> selectList() throws SQLException {
        return DaoSupport.selectList(context(), entityType(), null);
    }

    default List<T> selectList(Expr where) throws SQLException {
        return DaoSupport.selectList(context(), entityType(), where);
    }

    default PageResult<T> selectPage(PageRequest pageRequest) throws SQLException {
        return DaoSupport.selectPage(context(), entityType(), pageRequest, null);
    }

    default PageResult<T> selectPage(PageRequest pageRequest, Expr where) throws SQLException {
        return DaoSupport.selectPage(context(), entityType(), pageRequest, where);
    }

    default DaoContext context() {
        return DaoSupport.context(this);
    }

    @SuppressWarnings("unchecked")
    default Class<T> entityType() {
        return (Class<T>) DaoSupport.entityType(this);
    }
}

package io.lighting.lumen.example.dao;

import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.SQLException;
import java.util.List;

public interface OrderDao {
    @SqlTemplate("""
        SELECT o.id, o.order_no, o.status
        FROM @table(OrderRecord) o
        WHERE o.id = :id
        """)
    List<OrderRow> findById(Long id, RowMapper<OrderRow> mapper) throws SQLException;

    @SqlTemplate("""
        SELECT o.id, o.order_no, o.status
        FROM @table(OrderRecord) o
        @where {
          @if(filter != null && filter.status != null) { o.status = :filter.status }
          @if(filter != null && filter.ids != null && !filter.ids.isEmpty()) {
            AND o.id IN @in(:filter.ids)
          }
        }
        @orderBy(:sort, allowed = { CREATED_DESC : o.id DESC, STATUS_ASC : o.status ASC }, default = CREATED_DESC)
        @page(:page, :pageSize)
        """)
    List<OrderRow> search(
        OrderFilter filter,
        String sort,
        int page,
        int pageSize,
        RowMapper<OrderRow> mapper
    ) throws SQLException;

    @SqlTemplate("""
        UPDATE @table(OrderRecord)
        SET status = :status
        WHERE id = :id
        """)
    int updateStatus(Long id, String status) throws SQLException;

    @SqlTemplate("""
        DELETE FROM @table(OrderRecord)
        WHERE id = :id
        """)
    int deleteById(Long id) throws SQLException;

    @SqlTemplate("""
        SELECT o.id, o.order_no, o.status
        FROM @table(OrderRecord) o
        WHERE o.status = :status
        """)
    RenderedSql renderByStatus(String status) throws SQLException;
}

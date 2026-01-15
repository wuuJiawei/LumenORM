package io.lighting.lumen.example.todo.repo;

import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.template.annotations.SqlTemplate;
import java.sql.SQLException;

public interface TodoQueryDao {
    String TEMPLATE_FIND_BY_ID = """
        SELECT
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::id) AS id,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::title) AS title,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::description) AS description,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::completed) AS completed,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::createdAt) AS createdAt,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::updatedAt) AS updatedAt
        FROM @table(io.lighting.lumen.example.todo.model.TodoEntity) t
        WHERE t.@col(io.lighting.lumen.example.todo.model.TodoEntity::id) = :id
        """;

    String TEMPLATE_LIST = """
        SELECT
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::id) AS id,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::title) AS title,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::description) AS description,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::completed) AS completed,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::createdAt) AS createdAt,
          t.@col(io.lighting.lumen.example.todo.model.TodoEntity::updatedAt) AS updatedAt
        FROM @table(io.lighting.lumen.example.todo.model.TodoEntity) t
        @where {
          @if(completed != null) { t.@col(io.lighting.lumen.example.todo.model.TodoEntity::completed) = :completed }
        }
        ORDER BY t.@col(io.lighting.lumen.example.todo.model.TodoEntity::createdAt) DESC
        @page(:page, :pageSize)
        """;

    String TEMPLATE_COUNT = """
        SELECT COUNT(*)
        FROM @table(io.lighting.lumen.example.todo.model.TodoEntity) t
        @where {
          @if(completed != null) { t.@col(io.lighting.lumen.example.todo.model.TodoEntity::completed) = :completed }
        }
        """;

    @SqlTemplate(TEMPLATE_FIND_BY_ID)
    RenderedSql findById(long id) throws SQLException;

    @SqlTemplate(TEMPLATE_LIST)
    RenderedSql list(Boolean completed, int page, int pageSize) throws SQLException;

    @SqlTemplate(TEMPLATE_COUNT)
    RenderedSql count(Boolean completed) throws SQLException;
}

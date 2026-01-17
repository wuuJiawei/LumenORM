package io.lighting.lumen.example.todo.repo;

import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.example.todo.web.TodoResponse;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.SQLException;
import java.util.List;

public interface TodoQueryDao {
    String TEMPLATE_FIND_BY_ID = """
        SELECT
          ID AS id,
          TITLE AS title,
          DESCRIPTION AS description,
          COMPLETED AS completed,
          CREATED_AT AS createdAt,
          UPDATED_AT AS updatedAt
        FROM TODOS
        WHERE ID = :id
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
        @orderBy(:page.sort, allowed={
          createdAt: t.@col(io.lighting.lumen.example.todo.model.TodoEntity::createdAt),
          updatedAt: t.@col(io.lighting.lumen.example.todo.model.TodoEntity::updatedAt),
          title: t.@col(io.lighting.lumen.example.todo.model.TodoEntity::title),
          createdAtDesc: t.@col(io.lighting.lumen.example.todo.model.TodoEntity::createdAt) DESC
        }, default=createdAtDesc)
        @page(:page.page, :page.pageSize)
        """;

    String TEMPLATE_COUNT = """
        SELECT COUNT(*)
        FROM @table(io.lighting.lumen.example.todo.model.TodoEntity) t
        @where {
          @if(completed != null) { t.@col(io.lighting.lumen.example.todo.model.TodoEntity::completed) = :completed }
        }
        """;

    @SqlTemplate(TEMPLATE_FIND_BY_ID)
    RenderedSql findByIdSql(long id) throws SQLException;

    @SqlTemplate(TEMPLATE_FIND_BY_ID)
    TodoResponse findById(long id) throws SQLException;

    @SqlTemplate(TEMPLATE_FIND_BY_ID)
    TodoEntity findEntity(long id) throws SQLException;

    @SqlTemplate(TEMPLATE_LIST)
    RenderedSql listSql(Boolean completed, PageRequest page) throws SQLException;

    @SqlTemplate(TEMPLATE_LIST)
    List<TodoResponse> list(Boolean completed, PageRequest page) throws SQLException;

    @SqlTemplate(TEMPLATE_LIST)
    List<TodoEntity> listEntities(Boolean completed, PageRequest page) throws SQLException;

    @SqlTemplate(TEMPLATE_LIST)
    PageResult<TodoResponse> page(Boolean completed, PageRequest page) throws SQLException;

    @SqlTemplate(TEMPLATE_COUNT)
    long count(Boolean completed) throws SQLException;

    @SqlTemplate(TEMPLATE_COUNT)
    int countAsInt(Boolean completed) throws SQLException;
}

package io.lighting.lumen.example.todo.repo;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.example.todo.web.PageResponse;
import io.lighting.lumen.example.todo.web.TodoRequest;
import io.lighting.lumen.example.todo.web.TodoResponse;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TodoRepository {
    private final Db db;
    private final Dsl dsl;
    private final TodoQueryDao queryDao;
    private final Lumen lumen;

    public TodoRepository(Lumen lumen, TodoQueryDao queryDao) {
        this.lumen = lumen;
        this.db = lumen.db();
        this.dsl = lumen.dsl();
        this.queryDao = queryDao;
    }

    public TodoResponse create(TodoRequest request, LocalDateTime now) {
        boolean completed = request.completed() != null && request.completed();
        Table table = dsl.table(TodoEntity.class);
        InsertStmt stmt = dsl.insertInto(table)
            .columns(
                TodoEntity::getTitle,
                TodoEntity::getDescription,
                TodoEntity::getCompleted,
                TodoEntity::getCreatedAt,
                TodoEntity::getUpdatedAt
            )
            .row(request.title(), request.description(), completed, now, now)
            .build();
        RenderedSql renderedSql = lumen.renderer().render(stmt, Bindings.empty());
        Long id = executeAndReturnId(renderedSql);
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted todo not found"));
    }

    public Optional<TodoResponse> findById(long id) {
        RenderedSql renderedSql = renderFindById(id);
        List<TodoResponse> rows = fetchTodos(renderedSql);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    public Optional<TodoResponse> update(long id, TodoRequest request, LocalDateTime now) {
        boolean completed = request.completed() != null && request.completed();
        Table table = dsl.table(TodoEntity.class);
        UpdateStmt stmt = dsl.update(table)
            .set(TodoEntity::getTitle, request.title())
            .set(TodoEntity::getDescription, request.description())
            .set(TodoEntity::getCompleted, completed)
            .set(TodoEntity::getUpdatedAt, now)
            .where(table.col(TodoEntity::getId).eq(id))
            .build();
        RenderedSql renderedSql = lumen.renderer().render(stmt, Bindings.empty());
        int updated = execute(renderedSql);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean delete(long id) {
        Table table = dsl.table(TodoEntity.class);
        DeleteStmt stmt = dsl.deleteFrom(table)
            .where(table.col(TodoEntity::getId).eq(id))
            .build();
        RenderedSql renderedSql = lumen.renderer().render(stmt, Bindings.empty());
        return execute(renderedSql) > 0;
    }

    public PageResponse<TodoResponse> list(int page, int pageSize, Boolean completed) {
        RenderedSql listSql = renderList(completed, page, pageSize);
        List<TodoResponse> items = fetchTodos(listSql);
        RenderedSql countSql = renderCount(completed);
        long total = fetchCount(countSql);
        return new PageResponse<>(items, page, pageSize, total);
    }

    private List<TodoResponse> fetchTodos(RenderedSql renderedSql) {
        try {
            return db.fetch(Query.of(renderedSql), RowMappers.auto(TodoResponse.class));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load todos", ex);
        }
    }

    private long fetchCount(RenderedSql renderedSql) {
        try {
            List<Long> rows = db.fetch(Query.of(renderedSql), resultSet -> resultSet.getLong(1));
            if (rows.isEmpty()) {
                return 0L;
            }
            return rows.get(0);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to count todos", ex);
        }
    }

    private int execute(RenderedSql renderedSql) {
        try {
            return db.execute(Command.of(renderedSql));
        } catch (SQLException ex) {
            throw new IllegalStateException("Database command failed", ex);
        }
    }

    private Long executeAndReturnId(RenderedSql renderedSql) {
        try {
            return db.executeAndReturnGeneratedKey(
                Command.of(renderedSql),
                "ID",
                resultSet -> resultSet.getLong(1)
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to insert todo", ex);
        }
    }

    private RenderedSql renderFindById(long id) {
        try {
            return queryDao.findById(id);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to render findById query", ex);
        }
    }

    private RenderedSql renderList(Boolean completed, int page, int pageSize) {
        try {
            return queryDao.list(completed, page, pageSize);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to render list query", ex);
        }
    }

    private RenderedSql renderCount(Boolean completed) {
        try {
            return queryDao.count(completed);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to render count query", ex);
        }
    }
}

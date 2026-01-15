package io.lighting.lumen.example.todo.repo;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.example.todo.web.PageResponse;
import io.lighting.lumen.example.todo.web.TodoRequest;
import io.lighting.lumen.example.todo.web.TodoResponse;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.sql.SQLException;
import java.time.LocalDateTime;
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
        try {
            return Optional.ofNullable(queryDao.findById(id));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load todo", ex);
        }
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

    public PageResponse<TodoResponse> list(PageRequest pageRequest, Boolean completed) {
        try {
            var pageResult = queryDao.page(completed, pageRequest);
            return new PageResponse<>(pageResult.items(), pageResult.page(), pageResult.pageSize(), pageResult.total());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list todos", ex);
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

}

package io.lighting.lumen.example.todo.service;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.todo.model.TodoLabelEntity;
import io.lighting.lumen.example.todo.repo.TodoLabelDao;
import io.lighting.lumen.example.todo.web.PageResponse;
import io.lighting.lumen.example.todo.web.TodoLabelRequest;
import io.lighting.lumen.example.todo.web.TodoLabelResponse;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.page.Sort;
import io.lighting.lumen.sql.ast.Expr;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TodoLabelService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TodoLabelDao labelDao;
    private final Dsl dsl;

    public TodoLabelService(TodoLabelDao labelDao, Lumen lumen) {
        this.labelDao = labelDao;
        this.dsl = lumen.dsl();
    }

    public TodoLabelResponse create(TodoLabelRequest request) {
        validateRequest(request);
        LocalDateTime now = now();
        TodoLabelEntity entity = new TodoLabelEntity();
        entity.setName(request.name().trim());
        entity.setColor(normalizeColor(request.color()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            labelDao.insert(entity);
            TodoLabelEntity persisted = entity.getId() == null ? null : labelDao.selectById(entity.getId());
            return toResponse(persisted != null ? persisted : entity);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create label", ex);
        }
    }

    public Optional<TodoLabelResponse> findById(long id) {
        try {
            return Optional.ofNullable(toResponse(labelDao.selectById(id)));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load label", ex);
        }
    }

    public Optional<TodoLabelResponse> update(long id, TodoLabelRequest request) {
        validateRequest(request);
        try {
            TodoLabelEntity entity = labelDao.selectById(id);
            if (entity == null) {
                return Optional.empty();
            }
            entity.setName(request.name().trim());
            entity.setColor(normalizeColor(request.color()));
            entity.setUpdatedAt(now());
            int updated = labelDao.updateById(entity);
            if (updated == 0) {
                return Optional.empty();
            }
            return Optional.of(toResponse(entity));
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to update label", ex);
        }
    }

    public boolean delete(long id) {
        try {
            return labelDao.deleteById(id) > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete label", ex);
        }
    }

    public PageResponse<TodoLabelResponse> list(int page, int pageSize, String keyword, boolean searchCount) {
        validatePage(page, pageSize);
        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.asc("name"), searchCount);
        Expr where = null;
        if (keyword != null && !keyword.isBlank()) {
            Table table = dsl.table(TodoLabelEntity.class);
            where = table.col(TodoLabelEntity::getName).like("%" + keyword.trim() + "%");
        }
        try {
            PageResult<TodoLabelEntity> result = labelDao.selectPage(pageRequest, where);
            List<TodoLabelResponse> items = result.items().stream().map(this::toResponse).toList();
            return new PageResponse<>(items, result.page(), result.pageSize(), result.total());
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list labels", ex);
        }
    }

    private TodoLabelResponse toResponse(TodoLabelEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TodoLabelResponse(
            entity.getId(),
            entity.getName(),
            entity.getColor(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private void validateRequest(TodoLabelRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        return color.trim();
    }
}

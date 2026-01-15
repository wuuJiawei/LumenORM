package io.lighting.lumen.example.todo.service;

import io.lighting.lumen.example.todo.repo.TodoRepository;
import io.lighting.lumen.example.todo.web.PageResponse;
import io.lighting.lumen.example.todo.web.TodoRequest;
import io.lighting.lumen.example.todo.web.TodoResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TodoService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TodoRepository repository;

    public TodoService(TodoRepository repository) {
        this.repository = repository;
    }

    public TodoResponse create(TodoRequest request) {
        validateRequest(request);
        return repository.create(request, now());
    }

    public Optional<TodoResponse> findById(long id) {
        return repository.findById(id);
    }

    public Optional<TodoResponse> update(long id, TodoRequest request) {
        validateRequest(request);
        return repository.update(id, request, now());
    }

    public boolean delete(long id) {
        return repository.delete(id);
    }

    public PageResponse<TodoResponse> list(int page, int pageSize, Boolean completed) {
        validatePage(page, pageSize);
        return repository.list(page, pageSize, completed);
    }

    private void validateRequest(TodoRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
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
}

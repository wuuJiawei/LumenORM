package io.lighting.lumen.example.todo.web;

import io.lighting.lumen.example.todo.service.TodoLabelService;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/labels")
public class TodoLabelController {
    private final TodoLabelService service;

    public TodoLabelController(TodoLabelService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TodoLabelResponse> create(@RequestBody TodoLabelRequest request) {
        TodoLabelResponse response = service.create(request);
        URI location = URI.create("/labels/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoLabelResponse> getById(@PathVariable("id") long id) {
        Optional<TodoLabelResponse> response = service.findById(id);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoLabelResponse> update(
        @PathVariable("id") long id,
        @RequestBody TodoLabelRequest request
    ) {
        Optional<TodoLabelResponse> response = service.update(id, request);
        return response.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        if (!service.delete(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<TodoLabelResponse> list(
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "count", defaultValue = "true") boolean count
    ) {
        return service.list(page, pageSize, keyword, count);
    }
}

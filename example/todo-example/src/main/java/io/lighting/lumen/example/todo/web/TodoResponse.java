package io.lighting.lumen.example.todo.web;

import java.time.LocalDateTime;

public record TodoResponse(
    Long id,
    String title,
    String description,
    boolean completed,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

package io.lighting.lumen.example.todo.web;

import java.time.LocalDateTime;

public record TodoLabelResponse(
    Long id,
    String name,
    String color,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

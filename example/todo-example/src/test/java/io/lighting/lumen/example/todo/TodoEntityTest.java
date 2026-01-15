package io.lighting.lumen.example.todo;

import io.lighting.lumen.example.todo.model.TodoEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodoEntityTest {
    @Test
    void exposesAccessors() {
        TodoEntity entity = new TodoEntity();
        LocalDateTime now = LocalDateTime.now();

        entity.setId(1L);
        entity.setTitle("Task");
        entity.setDescription("Details");
        entity.setCompleted(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now.plusMinutes(5));

        assertEquals(1L, entity.getId());
        assertEquals("Task", entity.getTitle());
        assertEquals("Details", entity.getDescription());
        assertEquals(true, entity.getCompleted());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now.plusMinutes(5), entity.getUpdatedAt());
    }
}

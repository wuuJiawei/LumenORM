package io.lighting.lumen.example.todo.model;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.Table;
import java.time.LocalDateTime;

@Table(name = "TODO_LABELS")
public class TodoLabelEntity {
    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME")
    private String name;

    @Column(name = "COLOR")
    private String color;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "DELETED")
    private Integer deleted;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package io.lighting.lumen.example.todo.web;

public record TodoRequest(String title, String description, Boolean completed) {
}

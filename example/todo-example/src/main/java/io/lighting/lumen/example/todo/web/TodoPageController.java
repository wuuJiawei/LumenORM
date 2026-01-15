package io.lighting.lumen.example.todo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TodoPageController {
    @GetMapping({"/", "/ui"})
    public String todoPage() {
        return "todos";
    }
}

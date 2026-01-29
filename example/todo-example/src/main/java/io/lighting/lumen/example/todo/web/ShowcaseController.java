package io.lighting.lumen.example.todo.web;

import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.todo.model.TodoEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ShowcaseController {

    @GetMapping("/showcase")
    public String showcase(Model model) {
        // DSL Example: Show the code pattern (not executed)
        Table todo = new Dsl(null).table(TodoEntity.class).as("t");

        String dslCode = """
            // Build query programmatically with fluent API
            var todo = dsl.table(TodoEntity.class).as("t");

            var query = dsl.select(
                    todo.col("id").as("todo_id"),
                    todo.col("title").as("todo_title"),
                    todo.col("completed")
                )
                .from(todo)
                .where(w -> w.and(todo.col("completed").eq(false)))
                .orderBy(o -> o.desc(todo.col("createdAt").expr()))
                .page(1, 5)
                .build();

            // Generated SQL:
            // SELECT t.id AS todo_id, t.title AS todo_title, t.completed
            // FROM todos t
            // WHERE t.completed = false
            // ORDER BY t.created_at DESC
            // LIMIT ? OFFSET ?
            """;

        String dslGeneratedSql = """
            SELECT t.id AS todo_id, t.title AS todo_title, t.completed
            FROM todos t
            WHERE t.completed = false
            ORDER BY t.created_at DESC
            LIMIT ? OFFSET ?
            """;

        String templateCode = """
            // Write SQL directly with text blocks
            String sql = \"\"\"
                SELECT t.id AS todo_id, t.title AS todo_title, t.completed
                FROM todos t
                WHERE t.completed = false
                ORDER BY t.created_at DESC
                LIMIT ? OFFSET ?
                \"\"\";

            // Or with directives for dynamic SQL:
            String dynamicSql = \"\"\"
                SELECT * FROM todos t
                @if(includeCompleted) {
                    WHERE 1=1
                } @else {
                    WHERE t.completed = false
                }
                @orderBy(:sort, allowed = {CREATED_DESC: t.created_at DESC})
                @page(:page, :pageSize)
                \"\"\";
            """;

        String templateGeneratedSql = """
            -- With parameters: includeCompleted=true, sort=CREATED_DESC, page=1, pageSize=5
            SELECT * FROM todos t
            WHERE 1=1
            ORDER BY t.created_at DESC
            LIMIT 5 OFFSET 0
            """;

        Map<String, Object> dslExample = new HashMap<>();
        dslExample.put("title", "DSL (Fluent Builder)");
        dslExample.put("description", "Build queries programmatically with type-safe Java API");
        dslExample.put("code", dslCode);
        dslExample.put("generatedSql", dslGeneratedSql);
        dslExample.put("binds", List.of("5 (pageSize)", "0 (offset)"));
        dslExample.put("benefits", List.of(
            "Type-safe column references",
            "Compile-time validation",
            "IDE auto-completion",
            "Refactoring safe"
        ));

        Map<String, Object> templateExample = new HashMap<>();
        templateExample.put("title", "Template (Text Block)");
        templateExample.put("description", "Write SQL directly with embedded directives");
        templateExample.put("code", templateCode);
        templateExample.put("generatedSql", templateGeneratedSql);
        templateExample.put("binds", List.of("5 (pageSize)", "0 (offset)"));
        templateExample.put("benefits", List.of(
            "Native SQL feel",
            "Dynamic @if/@for directives",
            "Easy migration from MyBatis",
            "Full SQL expressiveness"
        ));

        model.addAttribute("dslExample", dslExample);
        model.addAttribute("templateExample", templateExample);
        model.addAttribute("activeTab", "showcase");

        return "showcase";
    }
}

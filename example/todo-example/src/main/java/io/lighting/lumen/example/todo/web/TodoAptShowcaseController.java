package io.lighting.lumen.example.todo.web;

import io.lighting.lumen.example.todo.service.TodoAptShowcaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

/**
 * 展示 LumenORM 特性的控制器。
 * <p>
 * 访问 /api/apt-showcase 运行所有示例。
 */
@RestController
@RequestMapping("/api/apt-showcase")
public class TodoAptShowcaseController {

    private final TodoAptShowcaseService showcaseService;

    public TodoAptShowcaseController(TodoAptShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping
    public String showcase() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>LumenORM Features Showcase</h1>");
        sb.append("<p>访问以下端点测试不同特性：</p>");
        sb.append("<ul>");
        sb.append("<li><a href='/api/apt-showcase/dsl'>DSL 查询构建</a></li>");
        sb.append("<li><a href='/api/apt-showcase/lambda'>Lambda DSL</a></li>");
        sb.append("<li><a href='/api/apt-showcase/conditions'>复杂条件</a></li>");
        sb.append("</ul>");
        return sb.toString();
    }

    @GetMapping("/dsl")
    public String dsl() throws SQLException {
        showcaseService.demonstrateDslQuery();
        return "DSL demo executed - check server console输出";
    }

    @GetMapping("/lambda")
    public String lambda() throws SQLException {
        showcaseService.demonstrateLambdaDsl();
        return "Lambda DSL demo executed - check server console输出";
    }

    @GetMapping("/conditions")
    public String conditions() throws SQLException {
        showcaseService.demonstrateComplexConditions();
        return "Complex conditions demo executed - check server console输出";
    }
}

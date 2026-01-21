package io.lighting.lumen.example.todo.web;

import io.lighting.lumen.example.todo.service.TodoAptShowcaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 展示 LumenORM APT 特性的控制器。
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
        sb.append("<h1>LumenORM APT Features Showcase</h1>");
        sb.append("<p>访问以下端点测试不同特性：</p>");
        sb.append("<ul>");
        sb.append("<li><a href='/api/apt-showcase/constants'>UserMeta 常量</a></li>");
        sb.append("<li><a href='/api/apt-showcase/methods'>UserMeta 方法</a></li>");
        sb.append("<li><a href='/api/apt-showcase/alias'>UserMetaTable 别名</a></li>");
        sb.append("<li><a href='/api/apt-showcase/lambda'>Lambda DSL</a></li>");
        sb.append("<li><a href='/api/apt-showcase/sql-template'>@SqlTemplate</a></li>");
        sb.append("<li><a href='/api/apt-showcase/base-dao'>BaseDao CRUD</a></li>");
        sb.append("<li><a href='/api/apt-showcase/table-info'>表信息</a></li>");
        sb.append("<li><a href='/api/apt-showcase/complex'>复杂查询</a></li>");
        sb.append("<li><a href='/api/apt-showcase/all'>运行全部</a></li>");
        sb.append("</ul>");
        return sb.toString();
    }

    @GetMapping("/constants")
    public String constants() {
        showcaseService.demonstrateUserMetaConstants();
        return "Constants demo executed - check server console输出";
    }

    @GetMapping("/methods")
    public String methods() {
        showcaseService.demonstrateUserMetaMethods();
        return "Methods demo executed - check server console输出";
    }

    @GetMapping("/alias")
    public String alias() {
        showcaseService.demonstrateUserMetaTableWithAlias();
        return "Alias demo executed - check server console输出";
    }

    @GetMapping("/lambda")
    public String lambda() {
        showcaseService.demonstrateLambdaDsl();
        return "Lambda DSL demo executed - check server console输出";
    }

    @GetMapping("/sql-template")
    public String sqlTemplate() {
        showcaseService.demonstrateSqlTemplate();
        return "@SqlTemplate demo executed - check server console输出";
    }

    @GetMapping("/base-dao")
    public String baseDao() {
        showcaseService.demonstrateBaseDaoCrud();
        return "BaseDao CRUD demo executed - check server console输出";
    }

    @GetMapping("/table-info")
    public String tableInfo() {
        showcaseService.demonstrateTableInfo();
        return "Table info demo executed - check server console输出";
    }

    @GetMapping("/complex")
    public String complex() {
        showcaseService.demonstrateComplexQuery();
        return "Complex query demo executed - check server console输出";
    }

    @GetMapping("/all")
    public String all() {
        showcaseService.runAllExamples();
        return "All demos executed - check server console输出";
    }
}

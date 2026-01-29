package io.lighting.lumen.example.todo.service;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.SelectStmt;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

/**
 * 展示 LumenORM 特性的综合示例服务。
 * <p>
 * 本示例展示以下特性：
 * <ul>
 *   <li>DSL 查询构建（类型安全）</li>
 *   <li>Lambda 风格 DSL（反射解析方法引用）</li>
 *   <li>BaseDao 基础 CRUD 操作</li>
 * </ul>
 */
@Service
public class TodoAptShowcaseService {

    private final Lumen lumen;
    private final Db db;
    private final Dsl dsl;

    public TodoAptShowcaseService(Lumen lumen, TodoBaseDao todoBaseDao) {
        this.lumen = lumen;
        this.db = lumen.db();
        this.dsl = lumen.dsl();
    }

    // ========== 1. DSL 查询构建示例 ==========

    /**
     * 使用 DSL 构建类型安全的查询。
     */
    public void demonstrateDslQuery() throws SQLException {
        var t = dsl.table(TodoEntity.class).as("t");

        // 构建查询
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(TodoEntity::getId).expr()),
                Dsl.item(t.col(TodoEntity::getTitle).expr()),
                Dsl.item(t.col(TodoEntity::getCompleted).expr())
            )
            .from(t)
            .where(w -> w.and(t.col(TodoEntity::getCompleted).eq(false)))
            .orderBy(o -> o.desc(t.col(TodoEntity::getCreatedAt).expr()))
            .page(1, 10)
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("DSL Query:");
        System.out.println("  SQL: " + rendered.sql());
        System.out.println("  Binds: " + rendered.binds());
    }

    // ========== 2. Lambda 风格 DSL 示例 ==========

    /**
     * 使用 Lambda 引用构建查询（运行时反射解析）。
     */
    public void demonstrateLambdaDsl() throws SQLException {
        var t = dsl.table(TodoEntity.class).as("t");

        // 使用 Lambda 风格的列引用
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(TodoEntity::getId).expr()),
                Dsl.item(t.col(TodoEntity::getTitle).expr()),
                Dsl.item(t.col(TodoEntity::getCompleted).expr())
            )
            .from(t)
            .where(w -> w.and(t.col(TodoEntity::getTitle).like("%test%")))
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("Lambda DSL:");
        System.out.println("  SQL: " + rendered.sql());
    }

    // ========== 3. 条件组合示例 ==========

    /**
     * 展示条件组合。
     */
    public void demonstrateComplexConditions() throws SQLException {
        var t = dsl.table(TodoEntity.class).as("t");

        // 条件组合
        SelectStmt stmt = dsl.select(
                Dsl.item(t.col(TodoEntity::getId).expr()),
                Dsl.item(t.col(TodoEntity::getTitle).expr())
            )
            .from(t)
            .where(w -> w
                .and(t.col(TodoEntity::getCompleted).eq(false))
                .and(t.col(TodoEntity::getTitle).like("%urgent%"))
            )
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("Complex Conditions:");
        System.out.println("  SQL: " + rendered.sql());
    }

    // ========== 4. BaseDao CRUD 操作接口 ==========

    /**
     * BaseDao 接口，定义基础的 CRUD 操作。
     */
    public interface TodoBaseDao {
        // 这些方法由 LumenDaoRegistrar 在运行时动态实现
    }
}

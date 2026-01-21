package io.lighting.lumen.example.todo.service;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.dao.BaseDao;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.example.todo.model.meta.TodoMeta;
import io.lighting.lumen.example.todo.repo.TodoQueryDao;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.template.annotations.SqlConst;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 展示 LumenORM 新 APT 特性的综合示例服务。
 * <p>
 * 本示例展示以下特性：
 * <ul>
 *   <li>APT 生成的 UserMeta 常量（如 {@code TodoMeta.ID}）</li>
 *   <li>APT 生成的 UserMeta 方法（如 {@code TodoMeta.id()}）</li>
 *   <li>APT 生成的 UserMetaTable 内部类（支持别名）</li>
 *   <li>Lambda 风格 DSL（反射解析方法引用）</li>
 *   <li>BaseDao 基础 CRUD 操作</li>
 *   <li>@SqlTemplate 注解方式</li>
 * </ul>
 */
@Service
public class TodoAptShowcaseService {

    private final Lumen lumen;
    private final Db db;
    private final Dsl dsl;
    private final TodoQueryDao queryDao;
    private final TodoBaseDao todoBaseDao;

    public TodoAptShowcaseService(Lumen lumen, TodoQueryDao queryDao, TodoBaseDao todoBaseDao) {
        this.lumen = lumen;
        this.db = lumen.db();
        this.dsl = lumen.dsl();
        this.queryDao = queryDao;
        this.todoBaseDao = todoBaseDao;
    }

    // ========== 1. APT 生成的 UserMeta 常量示例 ==========

    /**
     * 使用 UserMeta 常量获取列名字符串。
     * <p>
     * APT 生成的常量：
     * <pre>{@code
     * public static final String ID = "id";
     * public static final String TITLE = "title";
     * public static final String COMPLETED = "completed";
     * }</pre>
     */
    public void demonstrateUserMetaConstants() {
        // 直接使用列名字符串
        String idColumn = TodoMeta.ID;      // "id"
        String titleColumn = TodoMeta.TITLE; // "title"
        String completedColumn = TodoMeta.COMPLETED; // "completed"

        System.out.println("UserMeta Constants:");
        System.out.println("  ID column: " + idColumn);
        System.out.println("  TITLE column: " + titleColumn);
        System.out.println("  COMPLETED column: " + completedColumn);

        // 使用常量构建 SQL
        String sql = "SELECT " + idColumn + ", " + titleColumn + " FROM TODOS";
        System.out.println("  Generated SQL: " + sql);
    }

    // ========== 2. APT 生成的 UserMeta 方法示例 ==========

    /**
     * 使用 UserMeta 方法构建类型安全的 DSL。
     * <p>
     * APT 生成的方法：
     * <pre>{@code
     * public static ColumnRef id() { return ColumnRef.of("t", "id"); }
     * public static ColumnRef title() { return ColumnRef.of("t", "title"); }
     * }</pre>
     */
    public void demonstrateUserMetaMethods() {
        // 使用 APT 生成的方法（类型安全）
        var t = TodoMeta.TABLE;

        // 构建查询
        SelectStmt stmt = dsl.select(t.id(), t.title(), t.completed())
            .from(t)
            .where(t.title().like("%test%"))
            .orderBy(order -> order.desc(t.createdAt()))
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("UserMeta Methods DSL:");
        System.out.println("  SQL: " + rendered.sql());
    }

    // ========== 3. UserMetaTable 别名支持示例 ==========

    /**
     * 使用 UserMetaTable 进行多表关联查询。
     * <p>
     * APT 生成的内部类：
     * <pre>{@code
     * public final class TodoMetaTable {
     *     public ColumnRef id() { return ColumnRef.of(alias, "id"); }
     *     // ...
     * }
     * }</pre>
     */
    public void demonstrateUserMetaTableWithAlias() {
        // 使用别名进行多表查询
        var t = TodoMeta.TABLE.as("t");
        var t2 = TodoMeta.TABLE.as("t2");

        // 自连接查询
        SelectStmt stmt = dsl.select(t.id(), t.title(), t2.title().as("duplicate_title"))
            .from(t)
            .join(t2).on(t.title().eq(t2.title()).and(t.id().ne(t2.id())))
            .where(t.completed().isFalse())
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("UserMetaTable with Alias:");
        System.out.println("  SQL: " + rendered.sql());
    }

    // ========== 4. Lambda 风格 DSL 示例 ==========

    /**
     * 使用 Lambda 风格 DSL（允许反射解析方法引用）。
     * <p>
     * LambdaColumnRef 解析规则：
     * <ul>
     *   <li>{@code TodoEntity::getId} → "id"</li>
     *   <li>{@code TodoEntity::getTitle} → "title"</li>
     *   <li>{@code TodoEntity::isCompleted} → "completed" (boolean)</li>
     * </ul>
     */
    public void demonstrateLambdaDsl() {
        var table = dsl.table(TodoEntity.class);

        // 使用 Lambda 引用（反射解析）
        InsertStmt insertStmt = dsl.insertInto(table)
            .columns(
                TodoEntity::getTitle,
                TodoEntity::getDescription,
                TodoEntity::getCompleted
            )
            .row("Lambda Title", "Lambda Description", false)
            .build();

        RenderedSql rendered = lumen.renderer().render(insertStmt, Bindings.empty());
        System.out.println("Lambda DSL:");
        System.out.println("  SQL: " + rendered.sql());
    }

    // ========== 5. @SqlTemplate 注解示例 ==========

    /**
     * 使用 @SqlTemplate 注解定义 SQL 模板。
     */
    public void demonstrateSqlTemplate() {
        try {
            // 使用 APT 生成的实现类
            TodoResponse todo = queryDao.findById(1L);
            System.out.println("@SqlTemplate Result: " + todo);
        } catch (SQLException e) {
            throw new IllegalStateException("SQLTemplate failed", e);
        }
    }

    // ========== 6. BaseDao CRUD 操作示例 ==========

    /**
     * 使用 BaseDao 进行基础 CRUD 操作。
     */
    public void demonstrateBaseDaoCrud() {
        // 1. Insert
        TodoEntity newTodo = new TodoEntity();
        newTodo.setTitle("BaseDao Title");
        newTodo.setDescription("BaseDao Description");
        newTodo.setCompleted(false);
        newTodo.setCreatedAt(LocalDateTime.now());
        newTodo.setUpdatedAt(LocalDateTime.now());

        try {
            int inserted = todoBaseDao.insert(newTodo);
            System.out.println("BaseDao Insert: " + inserted + " row(s)");

            // 2. Select by ID
            Optional<TodoEntity> found = todoBaseDao.selectById(newTodo.getId());
            System.out.println("BaseDao Select: " + found.map(TodoEntity::getTitle).orElse("not found"));

            // 3. Update
            newTodo.setTitle("Updated Title");
            int updated = todoBaseDao.updateById(newTodo);
            System.out.println("BaseDao Update: " + updated + " row(s)");

            // 4. Delete
            int deleted = todoBaseDao.deleteById(newTodo.getId());
            System.out.println("BaseDao Delete: " + deleted + " row(s)");

        } catch (SQLException e) {
            throw new IllegalStateException("BaseDao CRUD failed", e);
        }
    }

    // ========== 7. 表信息方法示例 ==========

    /**
     * 使用 UserMeta 表信息方法。
     */
    public void demonstrateTableInfo() {
        // 获取表名
        String tableName = TodoMeta.tableName();
        System.out.println("Table Name: " + tableName);

        // 获取所有列名
        var columns = TodoMeta.columns();
        System.out.println("All Columns: " + columns);
    }

    // ========== 8. 综合查询示例 ==========

    /**
     * 综合使用多种方式构建复杂查询。
     */
    public void demonstrateComplexQuery() {
        var t = TodoMeta.TABLE.as("todo");

        // 构建复杂查询：带分页、排序、条件的查询
        SelectStmt stmt = dsl.select(t.id(), t.title(), t.completed())
            .from(t)
            .where(where -> {
                where.and(t.title().like("%important%"));
                where.and(t.completed().isFalse());
            })
            .orderBy(order -> order
                .desc(t.createdAt())
                .asc(t.title())
            )
            .page(1, 20)
            .build();

        RenderedSql rendered = lumen.renderer().render(stmt, Bindings.empty());
        System.out.println("Complex Query:");
        System.out.println("  SQL: " + rendered.sql());
        System.out.println("  Binds: " + rendered.binds());
    }

    /**
     * 运行所有示例。
     */
    public void runAllExamples() {
        System.out.println("=".repeat(60));
        System.out.println("LumenORM APT Features Showcase");
        System.out.println("=".repeat(60));

        demonstrateUserMetaConstants();
        System.out.println();

        demonstrateUserMetaMethods();
        System.out.println();

        demonstrateUserMetaTableWithAlias();
        System.out.println();

        demonstrateLambdaDsl();
        System.out.println();

        demonstrateSqlTemplate();
        System.out.println();

        demonstrateTableInfo();
        System.out.println();

        demonstrateComplexQuery();
        System.out.println();

        System.out.println("=".repeat(60));
    }

    /**
     * BaseDao 接口定义。
     */
    public interface TodoBaseDao extends BaseDao<TodoEntity, Long> {
    }

    /**
     * 带自定义查询的 DAO 接口。
     */
    public interface CustomTodoDao {
        @SqlTemplate("SELECT * FROM TODOS WHERE TITLE LIKE :title AND COMPLETED = :completed")
        List<TodoEntity> findByTitleAndCompleted(String title, Boolean completed) throws SQLException;

        @SqlTemplate("UPDATE TODOS SET COMPLETED = :completed WHERE ID = :id")
        int markAsCompleted(long id, boolean completed) throws SQLException;
    }
}

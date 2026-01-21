package io.lighting.lumen.example.todo;

import io.lighting.lumen.dsl.ColumnRef;
import io.lighting.lumen.example.todo.model.TodoEntity;
import io.lighting.lumen.example.todo.model.meta.TodoMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 APT 生成的 UserMeta 类是否正确。
 * <p>
 * 测试内容：
 * <ul>
 *   <li>列名字符串常量</li>
 *   <li>DSL 列引用方法</li>
 *   <li>默认表实例</li>
 *   <li>带别名的表实例</li>
 *   <li>表信息方法</li>
 * </ul>
 */
class TodoMetaAptGenerationTest {

    @Test
    void testConstantsExist() {
        // 验证列名字符串常量
        assertEquals("id", TodoMeta.ID);
        assertEquals("title", TodoMeta.TITLE);
        assertEquals("description", TodoMeta.DESCRIPTION);
        assertEquals("completed", TodoMeta.COMPLETED);
        assertEquals("created_at", TodoMeta.CREATED_AT);
        assertEquals("updated_at", TodoMeta.UPDATED_AT);
    }

    @Test
    void testMethodsReturnColumnRef() {
        // 验证 DSL 方法返回 ColumnRef
        ColumnRef id = TodoMeta.id();
        assertNotNull(id);
        assertEquals("t", id.tableAlias());
        assertEquals("id", id.columnName());

        ColumnRef title = TodoMeta.title();
        assertNotNull(title);
        assertEquals("t", title.tableAlias());
        assertEquals("title", title.columnName());
    }

    @Test
    void testDefaultTableInstance() {
        // 验证默认表实例
        var table = TodoMeta.TABLE;
        assertNotNull(table);
        assertEquals("TODOS", table.table());
        assertEquals("t", table.alias());
    }

    @Test
    void testTableWithAlias() {
        // 验证带别名的表实例
        var customTable = TodoMeta.as("custom");
        assertNotNull(customTable);
        assertEquals("TODOS", customTable.table());
        assertEquals("custom", customTable.alias());

        // 验证带别名表实例的列方法
        ColumnRef id = customTable.id();
        assertEquals("custom", id.tableAlias());
        assertEquals("id", id.columnName());
    }

    @Test
    void testTableInfoMethods() {
        // 验证表信息方法
        assertEquals("TODOS", TodoMeta.tableName());

        var columns = TodoMeta.columns();
        assertNotNull(columns);
        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("title"));
        assertTrue(columns.contains("description"));
        assertTrue(columns.contains("completed"));
        assertTrue(columns.contains("created_at"));
        assertTrue(columns.contains("updated_at"));
    }

    @Test
    void testTableMethodsReturnCorrectAlias() {
        // 验证 TABLE 的列方法使用默认别名 "t"
        var id = TodoMeta.TABLE.id();
        assertEquals("t", id.tableAlias());
        assertEquals("id", id.columnName());

        var title = TodoMeta.TABLE.title();
        assertEquals("t", title.tableAlias());
        assertEquals("title", title.columnName());
    }

    @Test
    void testMetaTableMethods() {
        // 验证 MetaTable 内部类的所有列方法
        var table = TodoMeta.TABLE;

        assertNotNull(table.id());
        assertNotNull(table.title());
        assertNotNull(table.description());
        assertNotNull(table.completed());
        assertNotNull(table.createdAt());
        assertNotNull(table.updatedAt());
    }
}

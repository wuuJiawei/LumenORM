package io.lighting.lumen.example.todo;

import io.lighting.lumen.dsl.ColumnRef;
import io.lighting.lumen.example.todo.model.TodoLabelEntity;
import io.lighting.lumen.example.todo.model.meta.TodoLabelMeta;
import io.lighting.lumen.sql.ast.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 APT 生成的 TodoLabelMeta 类（包含逻辑删除支持）。
 */
class TodoLabelMetaAptGenerationTest {

    @Test
    void testConstantsExist() {
        assertEquals("id", TodoLabelMeta.ID);
        assertEquals("name", TodoLabelMeta.NAME);
        assertEquals("color", TodoLabelMeta.COLOR);
        assertEquals("deleted", TodoLabelMeta.DELETED);
        assertEquals("created_at", TodoLabelMeta.CREATED_AT);
        assertEquals("updated_at", TodoLabelMeta.UPDATED_AT);
    }

    @Test
    void testMethodsReturnColumnRef() {
        ColumnRef id = TodoLabelMeta.id();
        assertNotNull(id);
        assertEquals("t", id.tableAlias());
        assertEquals("id", id.columnName());

        ColumnRef deleted = TodoLabelMeta.deletedAt();
        assertNotNull(deleted);
        assertEquals("t", deleted.tableAlias());
        assertEquals("deleted", deleted.columnName());
    }

    @Test
    void testTableInfo() {
        assertEquals("TODO_LABELS", TodoLabelMeta.tableName());

        var columns = TodoLabelMeta.columns();
        assertNotNull(columns);
        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("name"));
        assertTrue(columns.contains("color"));
        assertTrue(columns.contains("deleted"));
    }

    @Test
    void testNotDeletedExpression() {
        // 验证 notDeleted 方法返回正确的表达式
        var table = TodoLabelMeta.TABLE;
        Expr notDeleted = table.notDeleted();
        assertNotNull(notDeleted);
    }

    @Test
    void testDefaultTableInstance() {
        var table = TodoLabelMeta.TABLE;
        assertNotNull(table);
        assertEquals("TODO_LABELS", table.table());
        assertEquals("t", table.alias());
    }
}

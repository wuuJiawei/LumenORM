package io.lighting.lumen.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.TableRef;
import org.junit.jupiter.api.Test;

class ReflectionEntityMetaRegistryTest {

    private final ReflectionEntityMetaRegistry registry = new ReflectionEntityMetaRegistry();

    @Test
    void buildsMetaFromAnnotations() {
        EntityMeta meta = registry.metaOf(OrderEntity.class);

        assertEquals("orders", meta.table());
        assertEquals("id", meta.columnForField("id"));
        assertEquals("status", meta.columnForField("status"));
        assertEquals("created_at", meta.columnForField("createdAt"));
        assertEquals(4, meta.fieldToColumn().size());
        assertEquals(IdStrategy.UUID, meta.idMeta().orElseThrow().strategy());
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElseThrow();
        assertEquals("deleted", logicDeleteMeta.columnName());
        assertEquals(0, logicDeleteMeta.activeValue());
        assertEquals(1, logicDeleteMeta.deletedValue());
    }

    @Test
    void usesIdFieldNameWhenColumnAnnotationMissing() {
        EntityMeta meta = registry.metaOf(MinimalEntity.class);

        assertEquals("id", meta.columnForField("id"));
        assertEquals(1, meta.fieldToColumn().size());
    }

    @Test
    void cachesMetaInstances() {
        EntityMeta first = registry.metaOf(OrderEntity.class);
        EntityMeta second = registry.metaOf(OrderEntity.class);

        assertSame(first, second);
    }

    @Test
    void missingTableAnnotationFails() {
        assertThrows(IllegalArgumentException.class, () -> registry.metaOf(MissingTable.class));
    }

    @Test
    void duplicateColumnMappingsFail() {
        assertThrows(IllegalArgumentException.class, () -> registry.metaOf(DuplicateColumns.class));
    }

    @Test
    void duplicateLogicDeleteFails() {
        assertThrows(IllegalArgumentException.class, () -> registry.metaOf(DuplicateLogicDelete.class));
    }

    @Test
    void invalidLogicDeleteValueFails() {
        assertThrows(IllegalArgumentException.class, () -> registry.metaOf(InvalidLogicDelete.class));
    }

    @Test
    void identifierMacrosResolveSafeNames() {
        IdentifierMacros macros = new IdentifierMacros(registry);

        assertEquals("orders", macros.table(OrderEntity.class));
        assertEquals("status", macros.column(OrderEntity.class, "status"));
        TableRef tableRef = macros.tableRef(OrderEntity.class, "o");
        Expr.Column columnRef = macros.columnRef(OrderEntity.class, "o", "status");
        assertEquals("orders", tableRef.tableName());
        assertEquals("o", tableRef.alias());
        assertEquals("o", columnRef.tableAlias());
        assertEquals("status", columnRef.columnName());
    }

    @Table(name = "orders")
    private static final class OrderEntity extends BaseEntity {
        @Id(strategy = IdStrategy.UUID)
        @Column(name = "id")
        private long id;

        @Column(name = "status")
        private String status;

        @LogicDelete(active = "0", deleted = "1")
        @Column(name = "deleted")
        private int deleted;

        private String ignored;
    }

    private static class BaseEntity {
        @Column(name = "created_at")
        private String createdAt;
    }

    @Table(name = "minimal")
    private static final class MinimalEntity {
        @Id
        private long id;
    }

    private static final class MissingTable {
        @Column(name = "id")
        private long id;
    }

    @Table(name = "dup_columns")
    private static final class DuplicateColumns {
        @Column(name = "id")
        private long id;

        @Column(name = "id")
        private long otherId;
    }

    @Table(name = "dup_logic")
    private static final class DuplicateLogicDelete {
        @Id
        private long id;

        @LogicDelete
        private int deleted;

        @LogicDelete
        private int removed;
    }

    @Table(name = "invalid_logic")
    private static final class InvalidLogicDelete {
        @Id
        private long id;

        @LogicDelete(active = "x", deleted = "y")
        private int deleted;
    }
}

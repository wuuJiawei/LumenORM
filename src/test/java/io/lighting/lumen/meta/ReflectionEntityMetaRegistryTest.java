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
        assertEquals(3, meta.fieldToColumn().size());
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
        @Id
        @Column(name = "id")
        private long id;

        @Column(name = "status")
        private String status;

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
}

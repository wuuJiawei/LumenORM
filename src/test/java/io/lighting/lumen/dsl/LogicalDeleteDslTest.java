package io.lighting.lumen.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import org.junit.jupiter.api.Test;

class LogicalDeleteDslTest {
    @Test
    void buildsLogicalDeleteUpdate() {
        Dsl dsl = new Dsl(new ReflectionEntityMetaRegistry());
        io.lighting.lumen.dsl.Table orders = dsl.table(Order.class).as("o");

        UpdateStmt stmt = dsl.logicalDeleteFrom(orders)
            .where(orders.col("id").eq(Dsl.param("id")))
            .build();

        UpdateItem item = stmt.assignments().get(0);
        assertEquals("o", item.column().tableAlias());
        assertEquals("deleted", item.column().columnName());
        assertTrue(item.value() instanceof Expr.Literal);
        Expr.Literal literal = (Expr.Literal) item.value();
        assertEquals(1, literal.value());
    }

    @Test
    void exposesNotDeletedPredicate() {
        Dsl dsl = new Dsl(new ReflectionEntityMetaRegistry());
        io.lighting.lumen.dsl.Table orders = dsl.table(Order.class).as("o");

        Expr expr = orders.notDeleted();
        Expr.Compare compare = (Expr.Compare) expr;
        Expr.Column column = (Expr.Column) compare.left();
        Expr.Literal literal = (Expr.Literal) compare.right();

        assertEquals("deleted", column.columnName());
        assertEquals(0, literal.value());
    }

    @Table(name = "orders")
    private static final class Order {
        @Id
        private long id;

        @LogicDelete(active = "0", deleted = "1")
        private int deleted;
    }
}

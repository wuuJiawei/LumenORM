package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.UpdateItem;
import java.util.Objects;

public final class LogicalDelete {
    private final ColumnRef column;
    private final Object activeValue;
    private final Object deletedValue;

    LogicalDelete(ColumnRef column, Object activeValue, Object deletedValue) {
        this.column = Objects.requireNonNull(column, "column");
        this.activeValue = activeValue;
        this.deletedValue = deletedValue;
    }

    public ColumnRef column() {
        return column;
    }

    public Object activeValue() {
        return activeValue;
    }

    public Object deletedValue() {
        return deletedValue;
    }

    public Expr isActive() {
        return column.eq(activeValue);
    }

    public Expr isDeleted() {
        return column.eq(deletedValue);
    }

    UpdateItem updateItem() {
        return new UpdateItem(column.expr(), new Expr.Literal(deletedValue));
    }
}

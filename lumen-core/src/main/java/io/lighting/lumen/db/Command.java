package io.lighting.lumen.db;

import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import java.util.Objects;

public sealed interface Command permits RenderedCommand {
    RenderedSql render(SqlRenderer renderer);

    static Command of(RenderedSql renderedSql) {
        return new RenderedCommand(renderedSql);
    }
}

final class RenderedCommand implements Command {
    private final RenderedSql renderedSql;

    RenderedCommand(RenderedSql renderedSql) {
        this.renderedSql = Objects.requireNonNull(renderedSql, "renderedSql");
    }

    @Override
    public RenderedSql render(SqlRenderer renderer) {
        return renderedSql;
    }
}

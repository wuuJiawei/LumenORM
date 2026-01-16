package io.lighting.lumen.db;

import io.lighting.lumen.page.PageSql;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.Stmt;
import java.util.Objects;

public sealed interface Query permits AstQuery, RenderedQuery, CountQuery {
    RenderedSql render(SqlRenderer renderer);

    static Query of(Stmt stmt, Bindings bindings) {
        return new AstQuery(stmt, bindings);
    }

    static Query of(RenderedSql renderedSql) {
        return new RenderedQuery(renderedSql);
    }

    static Query count(Query source) {
        return new CountQuery(source);
    }
}

final class AstQuery implements Query {
    private final Stmt stmt;
    private final Bindings bindings;

    AstQuery(Stmt stmt, Bindings bindings) {
        this.stmt = Objects.requireNonNull(stmt, "stmt");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    @Override
    public RenderedSql render(SqlRenderer renderer) {
        Objects.requireNonNull(renderer, "renderer");
        return renderer.render(stmt, bindings);
    }
}

final class RenderedQuery implements Query {
    private final RenderedSql renderedSql;

    RenderedQuery(RenderedSql renderedSql) {
        this.renderedSql = Objects.requireNonNull(renderedSql, "renderedSql");
    }

    @Override
    public RenderedSql render(SqlRenderer renderer) {
        return renderedSql;
    }
}

final class CountQuery implements Query {
    private final Query source;

    CountQuery(Query source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public RenderedSql render(SqlRenderer renderer) {
        RenderedSql base = source.render(renderer);
        return PageSql.wrapCount(base);
    }
}

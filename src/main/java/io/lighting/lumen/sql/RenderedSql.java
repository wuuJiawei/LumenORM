package io.lighting.lumen.sql;

import java.util.List;
import java.util.Objects;

public record RenderedSql(String sql, List<Bind> binds) {
    public RenderedSql {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binds, "binds");
        binds = List.copyOf(binds);
    }

    public RenderedSql withBinds(List<Bind> newBinds) {
        return new RenderedSql(sql, newBinds);
    }
}

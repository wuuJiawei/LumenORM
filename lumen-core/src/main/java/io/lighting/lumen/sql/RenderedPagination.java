package io.lighting.lumen.sql;

import java.util.List;
import java.util.Objects;

public record RenderedPagination(String sqlFragment, List<Bind> binds) {
    public RenderedPagination {
        Objects.requireNonNull(sqlFragment, "sqlFragment");
        Objects.requireNonNull(binds, "binds");
        binds = List.copyOf(binds);
    }
}

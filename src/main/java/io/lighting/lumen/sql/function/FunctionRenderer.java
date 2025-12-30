package io.lighting.lumen.sql.function;

import io.lighting.lumen.sql.RenderedSql;
import java.util.List;

@FunctionalInterface
public interface FunctionRenderer {
    RenderedSql render(String name, List<RenderedSql> args);
}

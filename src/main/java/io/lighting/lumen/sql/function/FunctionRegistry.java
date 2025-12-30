package io.lighting.lumen.sql.function;

import io.lighting.lumen.sql.RenderedSql;
import java.util.List;

public interface FunctionRegistry {
    RenderedSql render(String name, List<RenderedSql> args);

    static FunctionRegistry standard() {
        return new DefaultFunctionRegistry();
    }
}

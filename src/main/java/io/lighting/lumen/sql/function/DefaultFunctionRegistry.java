package io.lighting.lumen.sql.function;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultFunctionRegistry implements FunctionRegistry {
    private final Map<String, FunctionRenderer> renderers = new ConcurrentHashMap<>();

    public DefaultFunctionRegistry register(String name, FunctionRenderer renderer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(renderer, "renderer");
        renderers.put(normalize(name), renderer);
        return this;
    }

    @Override
    public RenderedSql render(String name, List<RenderedSql> args) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(args, "args");
        FunctionRenderer renderer = renderers.get(normalize(name));
        if (renderer != null) {
            return renderer.render(name, args);
        }
        return defaultRender(name, args);
    }

    private RenderedSql defaultRender(String name, List<RenderedSql> args) {
        StringBuilder sql = new StringBuilder();
        List<Bind> binds = new ArrayList<>();
        sql.append(name).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            RenderedSql rendered = args.get(i);
            sql.append(rendered.sql());
            binds.addAll(rendered.binds());
        }
        sql.append(')');
        return new RenderedSql(sql.toString(), binds);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase();
    }
}

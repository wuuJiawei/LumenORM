package io.lighting.lumen.template;

import io.lighting.lumen.sql.RenderedSql;
import java.util.List;
import java.util.Objects;

public final class SqlTemplate {
    private final List<TemplateNode> nodes;

    SqlTemplate(List<TemplateNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    public static SqlTemplate parse(String template) {
        Objects.requireNonNull(template, "template");
        return new SqlTemplateParser(template).parse();
    }

    public RenderedSql render(TemplateContext context) {
        Objects.requireNonNull(context, "context");
        return new SqlTemplateRenderer().render(nodes, context);
    }

    List<TemplateNode> nodes() {
        return nodes;
    }
}

package io.lighting.lumen.template;

import io.lighting.lumen.sql.RenderedSql;
import java.util.List;
import java.util.Objects;

/**
 * SQL 模板对象。
 * <p>
 * 该类代表已经解析完成的模板 AST，可被多次渲染以生成 {@link RenderedSql}。
 * 模板语法由 {@link SqlTemplateParser} 解析，渲染时由 {@link SqlTemplateRenderer}
 * 结合 {@link TemplateContext} 完成参数绑定与 SQL 片段拼装。
 *
 */
public final class SqlTemplate {
    private final List<TemplateNode> nodes;

    SqlTemplate(List<TemplateNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    public static SqlTemplate parse(String template) {
        Objects.requireNonNull(template, "template");
        return new SqlTemplateParser(template).parse();
    }

    /**
     * 渲染模板并返回可执行 SQL。
     *
     * @param context 渲染上下文，包含参数绑定、方言、实体元数据等
     * @return 已渲染的 SQL 与绑定参数
     */
    public RenderedSql render(TemplateContext context) {
        Objects.requireNonNull(context, "context");
        return new SqlTemplateRenderer().render(nodes, context);
    }

    List<TemplateNode> nodes() {
        return nodes;
    }
}

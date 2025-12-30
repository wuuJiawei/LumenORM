package io.lighting.lumen.template;

import io.lighting.lumen.meta.IdentifierMacros;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.RenderedSql;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class SqlTemplateRenderer {

    RenderedSql render(List<TemplateNode> nodes, TemplateContext context) {
        StringBuilder sql = new StringBuilder();
        List<Bind> binds = new ArrayList<>();
        renderNodes(nodes, context, sql, binds);
        return new RenderedSql(sql.toString(), binds);
    }

    private void renderNodes(
        List<TemplateNode> nodes,
        TemplateContext context,
        StringBuilder sql,
        List<Bind> binds
    ) {
        for (TemplateNode node : nodes) {
            renderNode(node, context, sql, binds);
        }
    }

    private void renderNode(
        TemplateNode node,
        TemplateContext context,
        StringBuilder sql,
        List<Bind> binds
    ) {
        if (node instanceof TextNode text) {
            appendSql(sql, text.text());
        } else if (node instanceof ParamNode param) {
            appendParam(param.expression(), context, sql, binds);
        } else if (node instanceof IfNode ifNode) {
            Object condition = ifNode.condition().evaluate(context);
            if (TemplateExpression.toBoolean(condition)) {
                renderNodes(ifNode.body(), context, sql, binds);
            }
        } else if (node instanceof ForNode forNode) {
            Object source = forNode.source().evaluate(context);
            Iterable<?> iterable = toIterable(source, forNode.variable());
            for (Object item : iterable) {
                TemplateContext scoped = context.withLocal(forNode.variable(), item);
                renderNodes(forNode.body(), scoped, sql, binds);
            }
        } else if (node instanceof ClauseNode clause) {
            RenderedSql inner = renderToSql(clause.body(), context);
            String trimmed = stripLeadingConjunction(inner.sql().trim());
            if (!trimmed.isBlank()) {
                appendSql(sql, clause.keyword() + " " + trimmed);
                binds.addAll(inner.binds());
            }
        } else if (node instanceof OrNode orNode) {
            RenderedSql inner = renderToSql(orNode.body(), context);
            String trimmed = inner.sql().trim();
            if (!trimmed.isBlank()) {
                appendSql(sql, "OR " + trimmed);
                binds.addAll(inner.binds());
            }
        } else if (node instanceof InNode inNode) {
            appendIn(inNode.source(), context, sql, binds);
        } else if (node instanceof TableNode tableNode) {
            IdentifierMacros macros = context.macros();
            Class<?> entity = context.resolveEntity(tableNode.entityName());
            appendSql(sql, macros.table(entity));
        } else if (node instanceof ColumnNode columnNode) {
            IdentifierMacros macros = context.macros();
            Class<?> entity = context.resolveEntity(columnNode.entityName());
            appendSql(sql, macros.column(entity, columnNode.fieldName()));
        } else if (node instanceof PageNode pageNode) {
            appendPage(pageNode, context, sql, binds);
        } else if (node instanceof FnNode fnNode) {
            appendFunction(fnNode, context, sql, binds);
        } else {
            throw new IllegalArgumentException("Unsupported template node: " + node.getClass().getSimpleName());
        }
    }

    private void appendParam(
        TemplateExpression expression,
        TemplateContext context,
        StringBuilder sql,
        List<Bind> binds
    ) {
        Object value = expression.evaluate(context);
        sql.append('?');
        if (value instanceof Bind bind) {
            binds.add(bind);
        } else if (value == null) {
            binds.add(new Bind.NullValue(0));
        } else {
            binds.add(new Bind.Value(value, 0));
        }
    }

    private void appendIn(
        TemplateExpression expression,
        TemplateContext context,
        StringBuilder sql,
        List<Bind> binds
    ) {
        Object value = expression.evaluate(context);
        Iterable<?> iterable = toIterable(value, "in");
        List<Object> items = new ArrayList<>();
        for (Object item : iterable) {
            items.add(item);
        }
        if (items.isEmpty()) {
            sql.append("(NULL)");
            return;
        }
        sql.append('(');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
            Object item = items.get(i);
            if (item instanceof Bind bind) {
                binds.add(bind);
            } else if (item == null) {
                binds.add(new Bind.NullValue(0));
            } else {
                binds.add(new Bind.Value(item, 0));
            }
        }
        sql.append(')');
    }

    private void appendPage(PageNode pageNode, TemplateContext context, StringBuilder sql, List<Bind> binds) {
        int page = toInt(pageNode.page().evaluate(context), "page");
        int pageSize = toInt(pageNode.pageSize().evaluate(context), "pageSize");
        RenderedPagination pagination = context.dialect().renderPagination(page, pageSize, List.of());
        if (!pagination.sqlFragment().isBlank()) {
            appendSql(sql, pagination.sqlFragment());
        }
        binds.addAll(pagination.binds());
    }

    private void appendFunction(FnNode fnNode, TemplateContext context, StringBuilder sql, List<Bind> binds) {
        List<RenderedSql> args = new ArrayList<>();
        for (List<TemplateNode> argNodes : fnNode.args()) {
            args.add(renderToSql(argNodes, context));
        }
        RenderedSql rendered = context.functionRegistry().render(fnNode.name(), args);
        appendSql(sql, rendered.sql());
        binds.addAll(rendered.binds());
    }

    private RenderedSql renderToSql(List<TemplateNode> nodes, TemplateContext context) {
        StringBuilder sql = new StringBuilder();
        List<Bind> binds = new ArrayList<>();
        renderNodes(nodes, context, sql, binds);
        return new RenderedSql(sql.toString(), binds);
    }

    private Iterable<?> toIterable(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Iterable source '" + name + "' is null");
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        throw new IllegalArgumentException("Expected iterable for '" + name + "' but got " + value.getClass().getName());
    }

    private int toInt(Object value, String name) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Expected numeric value for '" + name + "'");
    }

    private String stripLeadingConjunction(String input) {
        String trimmed = input.trim();
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("AND")) {
            return stripKeyword(trimmed, 3);
        }
        if (upper.startsWith("OR")) {
            return stripKeyword(trimmed, 2);
        }
        return trimmed;
    }

    private String stripKeyword(String trimmed, int keywordLength) {
        if (trimmed.length() == keywordLength) {
            return "";
        }
        char next = trimmed.charAt(keywordLength);
        if (Character.isWhitespace(next)) {
            return trimmed.substring(keywordLength + 1).trim();
        }
        return trimmed;
    }

    private void appendSql(StringBuilder sql, String fragment) {
        if (fragment.isEmpty()) {
            return;
        }
        if (sql.length() == 0) {
            sql.append(fragment);
            return;
        }
        int start = 0;
        if (Character.isWhitespace(sql.charAt(sql.length() - 1))) {
            while (start < fragment.length() && Character.isWhitespace(fragment.charAt(start))) {
                start++;
            }
        }
        if (start >= fragment.length()) {
            return;
        }
        sql.append(fragment.substring(start));
    }
}

package io.lighting.lumen.template;

import io.lighting.lumen.meta.IdentifierMacros;
import io.lighting.lumen.page.Sort;
import io.lighting.lumen.page.SortDirection;
import io.lighting.lumen.page.SortItem;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.RenderedSql;
import java.lang.reflect.Array;
import java.util.ArrayList;
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
                if (shouldPrefixOr(sql)) {
                    appendSql(sql, "OR " + trimmed);
                } else {
                    appendSql(sql, trimmed);
                }
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
        } else if (node instanceof OrderByNode orderByNode) {
            appendOrderBy(orderByNode, context, sql, binds);
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
        ensureParamNotIdentifierPosition(sql);
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
            handleEmptyIn(context, sql);
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

    private void handleEmptyIn(TemplateContext context, StringBuilder sql) {
        switch (context.emptyInStrategy()) {
            case NULL -> sql.append("(NULL)");
            case ERROR -> throw new IllegalArgumentException("IN list is empty");
            case FALSE -> replaceWithFalsePredicate(sql);
        }
    }

    private void replaceWithFalsePredicate(StringBuilder sql) {
        int start = findPredicateStart(sql);
        if (start < 0) {
            throw new IllegalArgumentException("Cannot apply empty-IN strategy FALSE outside predicate context");
        }
        sql.setLength(start);
        appendSql(sql, "1=0");
    }

    private int findPredicateStart(StringBuilder sql) {
        int index = sql.length() - 1;
        while (index >= 0 && Character.isWhitespace(sql.charAt(index))) {
            index--;
        }
        while (index >= 0) {
            char ch = sql.charAt(index);
            if (ch == '(') {
                return index + 1;
            }
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                int end = index;
                while (index >= 0) {
                    char tokenChar = sql.charAt(index);
                    if (!Character.isLetterOrDigit(tokenChar) && tokenChar != '_') {
                        break;
                    }
                    index--;
                }
                String token = sql.substring(index + 1, end + 1).toUpperCase();
                if ("WHERE".equals(token) || "AND".equals(token) || "OR".equals(token)) {
                    int start = end + 1;
                    while (start < sql.length() && Character.isWhitespace(sql.charAt(start))) {
                        start++;
                    }
                    return start;
                }
            } else {
                index--;
            }
        }
        return -1;
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

    private void appendOrderBy(
        OrderByNode orderByNode,
        TemplateContext context,
        StringBuilder sql,
        List<Bind> binds
    ) {
        List<OrderSelection> selections = resolveOrderSelections(orderByNode.selection().evaluate(context));
        if (selections == null || selections.isEmpty()) {
            String fallback = orderByNode.defaultKey();
            if (fallback == null) {
                return;
            }
            selections = List.of(new OrderSelection(fallback, null));
        }
        List<String> fragments = new ArrayList<>();
        for (OrderSelection selection : selections) {
            String key = selection.key();
            if (key == null || key.isBlank()) {
                continue;
            }
            List<TemplateNode> allowedNodes = orderByNode.allowed().get(key);
            if (allowedNodes == null) {
                throw new IllegalArgumentException("@orderBy selection not allowed: " + key);
            }
            RenderedSql fragment = renderToSql(allowedNodes, context);
            if (!fragment.binds().isEmpty()) {
                throw new IllegalArgumentException("@orderBy fragments must not use parameters");
            }
            String trimmed = stripOrderByPrefix(fragment.sql().trim());
            if (trimmed.isBlank()) {
                continue;
            }
            SortDirection direction = selection.direction();
            if (direction != null && !hasTrailingDirection(trimmed)) {
                trimmed = trimmed + " " + direction.name();
            }
            fragments.add(trimmed);
        }
        if (fragments.isEmpty()) {
            return;
        }
        appendSql(sql, " ORDER BY " + String.join(", ", fragments));
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

    private String stripOrderByPrefix(String input) {
        String trimmed = input.trim();
        String upper = trimmed.toUpperCase();
        if (!upper.startsWith("ORDER BY")) {
            return trimmed;
        }
        int length = "ORDER BY".length();
        if (trimmed.length() == length) {
            return "";
        }
        char next = trimmed.charAt(length);
        if (Character.isWhitespace(next)) {
            return trimmed.substring(length + 1).trim();
        }
        return trimmed;
    }

    private List<OrderSelection> resolveOrderSelections(Object value) {
        if (value == null) {
            return null;
        }
        List<OrderSelection> selections = new ArrayList<>();
        if (value instanceof Sort sort) {
            for (SortItem item : sort.items()) {
                selections.add(new OrderSelection(item.key(), item.direction()));
            }
            return selections;
        }
        if (value instanceof SortItem item) {
            selections.add(new OrderSelection(item.key(), item.direction()));
            return selections;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addSelection(selections, item);
            }
            return selections;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addSelection(selections, Array.get(value, i));
            }
            return selections;
        }
        addSelection(selections, value);
        return selections;
    }

    private void addSelection(List<OrderSelection> selections, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Sort sort) {
            for (SortItem item : sort.items()) {
                selections.add(new OrderSelection(item.key(), item.direction()));
            }
            return;
        }
        if (value instanceof SortItem item) {
            selections.add(new OrderSelection(item.key(), item.direction()));
            return;
        }
        String key = resolveOrderKey(value);
        if (key != null) {
            selections.add(new OrderSelection(key, null));
        }
    }

    private String resolveOrderKey(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence sequence) {
            String key = sequence.toString().trim();
            return key.isEmpty() ? null : key;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString();
    }

    private boolean hasTrailingDirection(String fragment) {
        String trimmed = fragment.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return false;
        }
        String suffix = trimmed.substring(lastSpace + 1).toUpperCase();
        return "ASC".equals(suffix) || "DESC".equals(suffix);
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

    private record OrderSelection(String key, SortDirection direction) {
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

    private boolean shouldPrefixOr(StringBuilder sql) {
        TokenCursor cursor = new TokenCursor(sql);
        char last = cursor.peekNonWhitespace();
        if (last == '\0' || last == '(') {
            return false;
        }
        Token token = cursor.readToken();
        if (token == null) {
            return false;
        }
        String upper = token.value.toUpperCase();
        return !("AND".equals(upper) || "OR".equals(upper) || "WHERE".equals(upper));
    }

    private void ensureParamNotIdentifierPosition(StringBuilder sql) {
        TokenCursor cursor = new TokenCursor(sql);
        char last = cursor.peekNonWhitespace();
        if (last == '\0') {
            return;
        }
        if (last == '.' || last == '"' || last == '`' || last == '[') {
            throw new IllegalArgumentException("Parameters are not allowed in identifier positions");
        }
        Token lastToken = cursor.readToken();
        if (lastToken == null) {
            return;
        }
        String lastUpper = lastToken.value.toUpperCase();
        if (isIdentifierKeyword(lastUpper)) {
            throw new IllegalArgumentException("Parameters are not allowed in identifier positions");
        }
        if ("BY".equals(lastUpper)) {
            Token prev = cursor.readToken();
            if (prev != null) {
                String prevUpper = prev.value.toUpperCase();
                if ("ORDER".equals(prevUpper) || "GROUP".equals(prevUpper)) {
                    throw new IllegalArgumentException("Parameters are not allowed in identifier positions");
                }
            }
        }
        Token prev = cursor.readToken();
        if (prev != null && isIdentifierKeyword(prev.value.toUpperCase())) {
            throw new IllegalArgumentException("Parameters are not allowed in identifier positions");
        }
    }

    private boolean isIdentifierKeyword(String token) {
        return switch (token) {
            case "FROM", "JOIN", "UPDATE", "INTO", "TABLE", "SET" -> true;
            default -> false;
        };
    }

    private static final class TokenCursor {
        private final StringBuilder sql;
        private int index;

        private TokenCursor(StringBuilder sql) {
            this.sql = sql;
            this.index = sql.length() - 1;
        }

        private char peekNonWhitespace() {
            int pos = skipWhitespace(index);
            return pos >= 0 ? sql.charAt(pos) : '\0';
        }

        private Token readToken() {
            index = skipWhitespace(index);
            if (index < 0) {
                return null;
            }
            int end = index;
            while (index >= 0 && isTokenChar(sql.charAt(index))) {
                index--;
            }
            if (end == index) {
                return null;
            }
            int start = index + 1;
            return new Token(sql.substring(start, end + 1), start);
        }

        private int skipWhitespace(int start) {
            int pos = start;
            while (pos >= 0 && Character.isWhitespace(sql.charAt(pos))) {
                pos--;
            }
            return pos;
        }

        private boolean isTokenChar(char ch) {
            return Character.isLetterOrDigit(ch) || ch == '_';
        }
    }

    private record Token(String value, int start) {
    }
}

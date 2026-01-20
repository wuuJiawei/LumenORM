package io.lighting.lumen.template;

import java.util.Objects;

public final class SqlTemplatePageExpression {
    private final TemplateExpression page;
    private final TemplateExpression pageSize;

    SqlTemplatePageExpression(TemplateExpression page, TemplateExpression pageSize) {
        this.page = Objects.requireNonNull(page, "page");
        this.pageSize = Objects.requireNonNull(pageSize, "pageSize");
    }

    public int page(TemplateContext context) {
        return toInt(page.evaluate(context), "page");
    }

    public int pageSize(TemplateContext context) {
        return toInt(pageSize.evaluate(context), "pageSize");
    }

    private int toInt(Object value, String name) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid " + name + " value: " + value);
            }
        }
        throw new IllegalArgumentException("Invalid " + name + " value: " + value);
    }
}

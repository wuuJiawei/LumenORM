package io.lighting.lumen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateExpressionParserTest {
    private final TemplateContext context = new TemplateContext(
        Map.of(
            "count", 3,
            "name", "lumen",
            "flag", false,
            "filter", new Filter(List.of("a", "b"))
        ),
        new LimitOffsetDialect("\""),
        new ReflectionEntityMetaRegistry(),
        EntityNameResolvers.from(Map.of())
    );

    @Test
    void evaluatesLogicalAndComparison() {
        TemplateExpression expr = new TemplateExpressionParser(":count >= 2 && :name != ''").parse();
        assertEquals(true, expr.evaluate(context));
    }

    @Test
    void evaluatesUnaryNot() {
        TemplateExpression expr = new TemplateExpressionParser("!:flag").parse();
        assertEquals(true, expr.evaluate(context));
    }

    @Test
    void resolvesRecordAccessAndMethodCalls() {
        TemplateExpression expr = new TemplateExpressionParser(":filter.tags.isEmpty()").parse();
        assertEquals(false, expr.evaluate(context));
    }

    @Test
    void rejectsInvalidExpression() {
        assertThrows(IllegalArgumentException.class, () -> new TemplateExpressionParser("name &&").parse());
    }

    private record Filter(List<String> tags) {
    }
}

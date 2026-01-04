package io.lighting.lumen.example.dao;

import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.template.SqlTemplateAnalysis;
import io.lighting.lumen.template.SqlTemplateAnalyzer;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDaoTemplateTest {
    @Test
    void findByIdTemplateDeclaresBindings() throws Exception {
        SqlTemplateAnalysis analysis = analyze("findById", Long.class, RowMapper.class);

        assertTrue(analysis.bindings().contains("id"));
        assertFalse(analysis.orderByHasParams());
    }

    @Test
    void searchTemplateDeclaresBindings() throws Exception {
        SqlTemplateAnalysis analysis = analyze(
            "search",
            OrderFilter.class,
            String.class,
            int.class,
            int.class,
            RowMapper.class
        );

        assertTrue(analysis.bindings().containsAll(Set.of("filter", "sort", "page", "pageSize")));
        assertFalse(analysis.orderByHasParams());
    }

    @Test
    void updateStatusTemplateDeclaresBindings() throws Exception {
        SqlTemplateAnalysis analysis = analyze("updateStatus", Long.class, String.class);

        assertTrue(analysis.bindings().containsAll(Set.of("id", "status")));
    }

    @Test
    void renderByStatusTemplateDeclaresBindings() throws Exception {
        SqlTemplateAnalysis analysis = analyze("renderByStatus", String.class);

        assertTrue(analysis.bindings().contains("status"));
    }

    private SqlTemplateAnalysis analyze(String methodName, Class<?>... params) throws Exception {
        Method method = OrderDao.class.getMethod(methodName, params);
        SqlTemplate template = method.getAnnotation(SqlTemplate.class);
        if (template == null) {
            throw new IllegalStateException("Missing @SqlTemplate on " + methodName);
        }
        return SqlTemplateAnalyzer.analyze(template.value());
    }
}

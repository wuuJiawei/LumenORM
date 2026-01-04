package io.lighting.lumen.example;

import io.lighting.lumen.sql.BatchSql;
import io.lighting.lumen.sql.RenderedSql;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreExamplesTest {
    @Test
    void dslSelectExampleRendersSql() {
        RenderedSql rendered = CoreExamples.dslSelectExample();

        assertNotNull(rendered);
        assertTrue(rendered.sql().contains("SELECT"));
        assertTrue(rendered.sql().contains("FROM"));
        assertEquals(3, rendered.binds().size());
    }

    @Test
    void dslUpdateExampleRendersSql() {
        RenderedSql rendered = CoreExamples.dslUpdateExample();

        assertNotNull(rendered);
        assertTrue(rendered.sql().contains("UPDATE"));
        assertEquals(2, rendered.binds().size());
    }

    @Test
    void templateExampleRendersSql() {
        RenderedSql rendered = CoreExamples.templateExample();

        assertNotNull(rendered);
        assertTrue(rendered.sql().contains("ORDER BY"));
        assertTrue(rendered.sql().contains("LIMIT"));
        assertEquals(5, rendered.binds().size());
    }

    @Test
    void batchExampleBuildsBatches() {
        BatchSql batch = CoreExamples.batchExample();

        assertEquals(2, batch.totalBatches());
        assertEquals(2, batch.batches().size());
        assertEquals(2, batch.batches().get(0).size());
    }

    @Test
    void entityAccessorsExposeValues() {
        OrderRecord order = new OrderRecord();
        order.setId(1L);
        order.setOrderNo("NO-1");
        order.setStatus("NEW");
        order.setDeleted(0);

        assertEquals(1L, order.getId());
        assertEquals("NO-1", order.getOrderNo());
        assertEquals("NEW", order.getStatus());
        assertEquals(0, order.getDeleted());

        OrderItemRecord item = new OrderItemRecord();
        item.setId(10L);
        item.setOrderId(1L);
        item.setSku("SKU-1");

        assertEquals(10L, item.getId());
        assertEquals(1L, item.getOrderId());
        assertEquals("SKU-1", item.getSku());

        OrderModel model = new OrderModel();
        model.setId(2L);
        model.setOrderNo("NO-2");
        model.setStatus("PAID");
        model.setDeleted(0);

        assertEquals(2L, model.getId());
        assertEquals("NO-2", model.getOrderNo());
        assertEquals("PAID", model.getStatus());
        assertEquals(0, model.getDeleted());
    }
}

package io.lighting.lumen.example;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaDslH2ExampleTest {
    @Test
    void runsLambdaDslAgainstH2() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(H2ExampleSupport.dataSource("lumen_lambda"))
            .build();

        Db db = lumen.db();
        H2ExampleSupport.createOrdersTable(db);
        H2ExampleSupport.insertOrder(db, 1L, "NO-1", "NEW", 10);
        H2ExampleSupport.insertOrder(db, 2L, "NO-2", "PAID", 20);

        List<OrderRow> rows = db.dsl()
            .select(OrderRow.class, OrderRecord::getId, OrderRecord::getOrderNo, OrderRecord::getStatus)
            .from(OrderRecord.class)
            .where()
            .equals(OrderRecord::getStatus, "NEW")
            .toList();

        assertEquals(1, rows.size());
        assertEquals("NO-1", rows.get(0).orderNo());
    }
}

package io.lighting.lumen.example;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.sql.Bindings;
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

        Dsl dsl = lumen.dsl();
        Table orders = dsl.table(OrderRecord.class).as("o");

        List<OrderRow> rows = db.fetch(
            Query.of(
                dsl.select(
                        orders.col(OrderRecord::getId).as("id"),
                        orders.col(OrderRecord::getOrderNo).as("order_no"),
                        orders.col(OrderRecord::getStatus).as("status")
                    )
                    .from(orders)
                    .where(orders.col(OrderRecord::getStatus).eq(Dsl.param("status")))
                    .build(),
                Bindings.of("status", "NEW")
            ),
            rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
        );

        assertEquals(1, rows.size());
        assertEquals("NO-1", rows.get(0).orderNo());
    }
}

package io.lighting.lumen.example;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.template.annotations.SqlConst;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlTemplateH2ExampleTest {
    @SqlConst
    private static final String FIND_SQL = """
        SELECT o.id, o.order_no, o.status
        FROM @table(io.lighting.lumen.example.OrderRecord) o
        @where {
          @if(status != null) { o.status = :status }
          @if(ids != null && !ids.isEmpty()) { AND o.id IN @in(:ids) }
        }
        """;

    @Test
    void runsSqlTemplateAgainstH2() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(H2ExampleSupport.dataSource("lumen_template"))
            .build();

        Db db = lumen.db();
        H2ExampleSupport.createOrdersTable(db);
        H2ExampleSupport.insertOrder(db, 1L, "NO-1", "NEW", 10);
        H2ExampleSupport.insertOrder(db, 2L, "NO-2", "PAID", 20);

        List<OrderRow> rows = db.run(
            FIND_SQL,
            Bindings.of("status", "NEW", "ids", List.of(1L, 2L)),
            rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
        );

        assertEquals(1, rows.size());
        assertEquals("NO-1", rows.get(0).orderNo());
    }
}

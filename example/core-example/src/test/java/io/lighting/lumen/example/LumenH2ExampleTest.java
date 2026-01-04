package io.lighting.lumen.example;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.SqlLog;
import io.lighting.lumen.sql.Bindings;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LumenH2ExampleTest {
    @Test
    void runsSqlAgainstH2() throws SQLException {
        Logger logger = LoggerFactory.getLogger(LumenH2ExampleTest.class);
        SqlLog sqlLog = SqlLog.builder()
            .mode(SqlLog.Mode.INLINE)
            .includeElapsed(true)
            .includeRowCount(true)
            .sink(logger::info)
            .build();

        Lumen lumen = Lumen.builder()
            .dataSource(H2ExampleSupport.dataSource("lumen_example"))
            .observers(List.of(sqlLog))
            .build();

        Db db = lumen.db();

        H2ExampleSupport.createOrdersTable(db);
        H2ExampleSupport.insertOrder(db, 1L, "NO-1", "NEW", 10);

        List<OrderRow> rows = db.run(
            "SELECT id, order_no, status FROM orders WHERE status = :status",
            Bindings.of("status", "NEW"),
            rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
        );

        assertFalse(rows.isEmpty());
        assertEquals("NO-1", rows.get(0).orderNo());
    }

}

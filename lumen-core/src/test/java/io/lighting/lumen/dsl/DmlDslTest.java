package io.lighting.lumen.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import java.util.List;
import org.junit.jupiter.api.Test;

class DmlDslTest {
    private final TestEntityMetaRegistry registry = new TestEntityMetaRegistry();
    private final SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));

    @Test
    void buildsInsertStatements() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class);

        RenderedSql rendered = renderer.render(
            dsl.insertInto(orders)
                .columns(orders.col("id"), orders.col("status"))
                .row(Dsl.param("id"), "NEW")
                .build(),
            Bindings.of("id", 10)
        );

        assertEquals(
            "INSERT INTO \"orders\" (\"id\", \"status\") VALUES (?, ?)",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value(10, 0), new Bind.Value("NEW", 0)),
            rendered.binds()
        );
    }

    @Test
    void buildsUpdateStatements() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class);

        RenderedSql rendered = renderer.render(
            dsl.update(orders)
                .set(orders.col("status"), Dsl.param("status"))
                .where(orders.col("id").eq(Dsl.param("id")))
                .build(),
            Bindings.of("status", "PAID", "id", 7)
        );

        assertEquals(
            "UPDATE \"orders\" SET \"status\" = ? WHERE \"id\" = ?",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value("PAID", 0), new Bind.Value(7, 0)),
            rendered.binds()
        );
    }

    @Test
    void buildsDeleteStatements() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class);

        RenderedSql rendered = renderer.render(
            dsl.deleteFrom(orders)
                .where(orders.col("id").eq(Dsl.param("id")))
                .build(),
            Bindings.of("id", 3)
        );

        assertEquals(
            "DELETE FROM \"orders\" WHERE \"id\" = ?",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value(3, 0)),
            rendered.binds()
        );
    }

    @io.lighting.lumen.meta.Table(name = "orders")
    private static final class OrderEntity {
        @Id
        @Column(name = "id")
        private long id;

        @Column(name = "status")
        private String status;
    }
}

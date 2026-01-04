package io.lighting.lumen.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import java.util.List;
import org.junit.jupiter.api.Test;

class FluentDslTest {
    private final ReflectionEntityMetaRegistry registry = new ReflectionEntityMetaRegistry();
    private final SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));

    @Test
    void buildsSelectWithWhereOrderAndPaging() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class).as("o");

        RenderedSql rendered = renderer.render(
            dsl.select(
                    orders.col("id").as("order_id"),
                    orders.col("status").select()
                )
                .from(orders)
                .where(where -> where.and(orders.col("status").eq(Dsl.param("status"))))
                .orderBy(order -> order.desc(orders.col("createdAt").expr()).asc(orders.col("id").expr()))
                .page(2, 10)
                .build(),
            Bindings.of("status", "PAID")
        );

        assertEquals(
            "SELECT \"o\".\"id\" AS \"order_id\", \"o\".\"status\" FROM \"orders\" \"o\" "
                + "WHERE \"o\".\"status\" = ? ORDER BY \"o\".\"created_at\" DESC, \"o\".\"id\" ASC "
                + "LIMIT ? OFFSET ? ",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value("PAID", 0), new Bind.Value(10, 0), new Bind.Value(10, 0)),
            rendered.binds()
        );
    }

    @Test
    void composesOrGroupsInWhere() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class).as("o");
        Table customers = dsl.table(CustomerEntity.class).as("c");

        RenderedSql rendered = renderer.render(
            dsl.select(orders.col("id").select(), customers.col("name").as("customer_name"))
                .from(orders)
                .join(customers)
                .on(orders.col("customerId").eq(customers.col("id").expr()))
                .where(where -> where.orGroup(or -> or
                    .or(customers.col("name").like("%acme%"))
                    .or(customers.col("name").like("%light%"))
                ))
                .build(),
            Bindings.empty()
        );

        assertEquals(
            "SELECT \"o\".\"id\", \"c\".\"name\" AS \"customer_name\" FROM \"orders\" \"o\" "
                + "JOIN \"customers\" \"c\" ON \"o\".\"customer_id\" = \"c\".\"id\" "
                + "WHERE (\"c\".\"name\" LIKE ? OR \"c\".\"name\" LIKE ?)",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value("%acme%", 0), new Bind.Value("%light%", 0)),
            rendered.binds()
        );
    }

    @Test
    void skipsWhereWhenPredicateBuilderEmpty() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class).as("o");

        RenderedSql rendered = renderer.render(
            dsl.select(orders.col("id").select())
                .from(orders)
                .where(where -> { })
                .build(),
            Bindings.empty()
        );

        assertEquals(
            "SELECT \"o\".\"id\" FROM \"orders\" \"o\"",
            rendered.sql()
        );
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void supportsOrderByWhitelistSelection() {
        Dsl dsl = new Dsl(registry);
        Table orders = dsl.table(OrderEntity.class).as("o");

        RenderedSql rendered = renderer.render(
            dsl.select(orders.col("id").select())
                .from(orders)
                .orderBy(order -> order
                    .allow(Sort.CREATED_DESC, orders.col("createdAt").desc())
                    .allow(Sort.ID_ASC, orders.col("id").asc())
                    .use(Sort.ID_ASC, Sort.CREATED_DESC))
                .build(),
            Bindings.empty()
        );

        assertEquals(
            "SELECT \"o\".\"id\" FROM \"orders\" \"o\" ORDER BY \"o\".\"id\" ASC",
            rendered.sql()
        );
        assertEquals(List.of(), rendered.binds());
    }

    @io.lighting.lumen.meta.Table(name = "orders")
    private static final class OrderEntity {
        @Id
        @Column(name = "id")
        private long id;

        @Column(name = "status")
        private String status;

        @Column(name = "created_at")
        private String createdAt;

        @Column(name = "customer_id")
        private long customerId;
    }

    @io.lighting.lumen.meta.Table(name = "customers")
    private static final class CustomerEntity {
        @Id
        @Column(name = "id")
        private long id;

        @Column(name = "name")
        private String name;
    }

    private enum Sort {
        CREATED_DESC,
        ID_ASC
    }
}

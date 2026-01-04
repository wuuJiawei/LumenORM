package io.lighting.lumen.example;

import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.BatchSql;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.ast.Stmt;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.EntityNameResolvers;
import io.lighting.lumen.template.SqlTemplate;
import io.lighting.lumen.template.TemplateContext;
import java.util.List;

public final class CoreExamples {
    private CoreExamples() {
    }

    public static RenderedSql dslSelectExample() {
        EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        Dsl dsl = new Dsl(metaRegistry);
        Table orders = dsl.table(OrderRecord.class).as("o");
        Table items = dsl.table(OrderItemRecord.class).as("i");

        Stmt stmt = dsl.select(
                orders.col(OrderRecord::getId).as("order_id"),
                orders.col(OrderRecord::getStatus).as("status"),
                items.col(OrderItemRecord::getSku).as("sku")
            )
            .from(orders)
            .leftJoin(items)
            .on(items.col(OrderItemRecord::getOrderId).eq(orders.col(OrderRecord::getId)))
            .where(orders.col(OrderRecord::getStatus).eq(Dsl.param("status")))
            .orderBy(orders.col(OrderRecord::getId).desc())
            .page(1, 20)
            .build();

        return render(stmt, Bindings.of("status", "NEW"));
    }

    public static RenderedSql dslUpdateExample() {
        EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        Dsl dsl = new Dsl(metaRegistry);
        Table orders = dsl.table(OrderRecord.class);

        Stmt stmt = dsl.update(orders)
            .set(OrderRecord::getStatus, Dsl.param("status"))
            .where(orders.col(OrderRecord::getId).eq(Dsl.param("id")))
            .build();

        return render(stmt, Bindings.of("status", "PAID", "id", 100L));
    }

    public static RenderedSql templateExample() {
        String sql = """
            SELECT o.id, o.status
            FROM @table(OrderRecord) o
            @where {
              @if(status != null) { o.status = :status }
              @if(!ids.isEmpty()) { AND o.id IN @in(:ids) }
            }
            @orderBy(:sort, allowed = { CREATED_DESC : o.id DESC, STATUS_ASC : o.status ASC }, default = CREATED_DESC)
            @page(:page, :pageSize)
            """;

        EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
        Dialect dialect = exampleDialect();
        EntityNameResolver resolver = EntityNameResolvers.auto();
        Bindings bindings = Bindings.of(
            "status", "NEW",
            "ids", List.of(10L, 20L),
            "sort", "STATUS_ASC",
            "page", 1,
            "pageSize", 50
        );
        SqlTemplate template = SqlTemplate.parse(sql);
        TemplateContext context = new TemplateContext(bindings.asMap(), dialect, metaRegistry, resolver);
        return template.render(context);
    }

    public static BatchSql batchExample() {
        RenderedSql template = new RenderedSql(
            "UPDATE \"orders\" SET \"status\" = ? WHERE \"id\" = ?",
            List.of()
        );

        return BatchSql.builder(template)
            .add(List.of(new Bind.Value("PAID", 0), new Bind.Value(100L, 0)))
            .add(List.of(new Bind.Value("CANCELLED", 0), new Bind.Value(101L, 0)))
            .batchSize(500)
            .build();
    }

    private static RenderedSql render(Stmt stmt, Bindings bindings) {
        SqlRenderer renderer = new SqlRenderer(exampleDialect());
        return renderer.render(stmt, bindings);
    }

    private static Dialect exampleDialect() {
        return new LimitOffsetDialect("ansi", "\"");
    }
}

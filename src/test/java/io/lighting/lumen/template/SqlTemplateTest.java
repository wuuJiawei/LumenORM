package io.lighting.lumen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.sql.function.DefaultFunctionRegistry;
import io.lighting.lumen.sql.function.FunctionRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlTemplateTest {
    private final ReflectionEntityMetaRegistry registry = new ReflectionEntityMetaRegistry();
    private final LimitOffsetDialect dialect = new LimitOffsetDialect("\"");

    @Test
    void rendersMacrosAndParams() {
        String template = "SELECT @col(Order::id) FROM @table(Order) o WHERE o.@col(Order::status) = :status";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("status", "PAID")));

        assertEquals("SELECT id FROM orders o WHERE o.status = ?", rendered.sql());
        assertEquals(List.of(new Bind.Value("PAID", 0)), rendered.binds());
    }

    @Test
    void rendersWhereIfAndInDirectives() {
        String template = "SELECT * FROM orders o @where {"
            + " @if(!:includeDeleted) { AND o.deleted_at IS NULL }"
            + " @if(:statuses) { AND o.status IN @in(:statuses) }"
            + " }";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(
            context(Bindings.of("includeDeleted", false, "statuses", List.of("PAID", "NEW")))
        );

        assertEquals(
            "SELECT * FROM orders o WHERE o.deleted_at IS NULL AND o.status IN (?, ?)",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value("PAID", 0), new Bind.Value("NEW", 0)),
            rendered.binds()
        );
    }

    @Test
    void rendersForWithOrBlocks() {
        String template = "SELECT * FROM t @where { @for(tag : :tags) { @or { t.tag = :tag } } }";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("tags", List.of("a", "b"))));

        assertEquals("SELECT * FROM t WHERE t.tag = ? OR t.tag = ?", rendered.sql());
        assertEquals(
            List.of(new Bind.Value("a", 0), new Bind.Value("b", 0)),
            rendered.binds()
        );
    }

    @Test
    void rendersPaginationDirective() {
        String template = "SELECT * FROM t@page(:page, :pageSize)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("page", 2, "pageSize", 10)));

        assertEquals("SELECT * FROM t LIMIT ? OFFSET ? ", rendered.sql());
        assertEquals(
            List.of(new Bind.Value(10, 0), new Bind.Value(10, 0)),
            rendered.binds()
        );
    }

    @Test
    void expandsEmptyInList() {
        String template = "SELECT * FROM t WHERE id IN @in(:ids)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("ids", List.of())));

        assertEquals("SELECT * FROM t WHERE id IN (NULL)", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void supportsComparisonExpressions() {
        String template = "SELECT * FROM t @where { @if(:count > 0 && :name != '') { AND name = :name } }";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("count", 2, "name", "lamp")));

        assertEquals("SELECT * FROM t WHERE name = ?", rendered.sql());
        assertEquals(List.of(new Bind.Value("lamp", 0)), rendered.binds());
    }

    @Test
    void rendersFunctionMacros() {
        String template = "SELECT @fn.count_distinct(o.id) FROM orders o";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        DefaultFunctionRegistry registry = new DefaultFunctionRegistry()
            .register("count_distinct", (name, args) -> {
                RenderedSql arg = args.get(0);
                return new RenderedSql("COUNT(DISTINCT " + arg.sql() + ")", arg.binds());
            });

        RenderedSql rendered = sqlTemplate.render(context(Bindings.empty(), registry));

        assertEquals("SELECT COUNT(DISTINCT o.id) FROM orders o", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    private TemplateContext context(Bindings bindings) {
        return context(bindings, FunctionRegistry.standard());
    }

    private TemplateContext context(Bindings bindings, FunctionRegistry functionRegistry) {
        return new TemplateContext(
            bindings.asMap(),
            dialect,
            registry,
            EntityNameResolvers.from(Map.of("Order", OrderEntity.class)),
            functionRegistry
        );
    }

    @Table(name = "orders")
    private static final class OrderEntity {
        @Id
        @Column(name = "id")
        private long id;

        @Column(name = "status")
        private String status;
    }
}

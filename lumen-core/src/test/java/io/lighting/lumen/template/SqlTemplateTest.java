package io.lighting.lumen.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlTemplateTest {
    private final TestEntityMetaRegistry registry = new TestEntityMetaRegistry();
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
    void rendersEmptyInAsFalsePredicate() {
        String template = "SELECT * FROM t WHERE id IN @in(:ids)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(
            context(Bindings.of("ids", List.of()), EmptyInStrategy.FALSE)
        );

        assertEquals("SELECT * FROM t WHERE 1=0", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void rejectsEmptyInWhenConfiguredToError() {
        String template = "SELECT * FROM t WHERE id IN @in(:ids)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);

        assertThrows(
            IllegalArgumentException.class,
            () -> sqlTemplate.render(context(Bindings.of("ids", List.of()), EmptyInStrategy.ERROR))
        );
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
    void rendersOrderBySelectionFromWhitelist() {
        String template = "SELECT * FROM orders o "
            + "@orderBy(:sort, allowed = { CREATED_DESC : o.created_at DESC, ID_ASC : o.id ASC }, "
            + "default = CREATED_DESC)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("sort", Sort.ID_ASC)));

        assertEquals("SELECT * FROM orders o ORDER BY o.id ASC", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void rendersOrderByDefaultWhenSelectionNull() {
        String template = "SELECT * FROM orders o "
            + "@orderBy(:sort, allowed = { CREATED_DESC : o.created_at DESC, ID_ASC : o.id ASC }, "
            + "default = CREATED_DESC)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(context(Bindings.of("sort", null)));

        assertEquals("SELECT * FROM orders o ORDER BY o.created_at DESC", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void rendersDialectConditionalBlocks() {
        String template = "SELECT * FROM orders o "
            + "@if(::dialect == 'mysql') { LIMIT 1}"
            + "@if(::dialect == 'oracle') { ROWNUM <= 1}";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);
        RenderedSql rendered = sqlTemplate.render(dialectContext("mysql"));

        assertEquals("SELECT * FROM orders o LIMIT 1", rendered.sql());
        assertEquals(List.of(), rendered.binds());
    }

    @Test
    void rejectsUnknownOrderBySelection() {
        String template = "SELECT * FROM orders o "
            + "@orderBy(:sort, allowed = { CREATED_DESC : o.created_at DESC }, default = CREATED_DESC)";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);

        assertThrows(
            IllegalArgumentException.class,
            () -> sqlTemplate.render(context(Bindings.of("sort", "BAD"))));
    }

    @Test
    void rejectsParamInFromClause() {
        String template = "SELECT * FROM :table";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);

        assertThrows(
            IllegalArgumentException.class,
            () -> sqlTemplate.render(context(Bindings.of("table", "orders"))));
    }

    @Test
    void rejectsParamInOrderByClause() {
        String template = "SELECT * FROM orders o ORDER BY :sort";
        SqlTemplate sqlTemplate = SqlTemplate.parse(template);

        assertThrows(
            IllegalArgumentException.class,
            () -> sqlTemplate.render(context(Bindings.of("sort", "created_at"))));
    }

    private TemplateContext context(Bindings bindings) {
        return context(bindings, EmptyInStrategy.NULL);
    }

    private TemplateContext context(Bindings bindings, EmptyInStrategy emptyInStrategy) {
        return new TemplateContext(
            bindings.asMap(),
            dialect,
            registry,
            EntityNameResolvers.from(Map.of("Order", OrderEntity.class)),
            emptyInStrategy
        );
    }

    private TemplateContext dialectContext(String id) {
        return new TemplateContext(
            Bindings.empty().asMap(),
            new LimitOffsetDialect(id, "\""),
            registry,
            EntityNameResolvers.from(Map.of("Order", OrderEntity.class))
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

    private enum Sort {
        CREATED_DESC,
        ID_ASC
    }
}

package io.lighting.lumen.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.Join;
import io.lighting.lumen.sql.ast.JoinType;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.TableRef;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

class SqlRendererTest {

    @Test
    void renderSelectWithWhereOrderPaging(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        SelectStmt stmt = new SelectStmt(
            List.of(
                new SelectItem(new Expr.Column("o", "id"), "order_id"),
                new SelectItem(new Expr.Column("o", "status"), null)
            ),
            new TableRef("sales.orders", "o"),
            List.of(),
            new Expr.Compare(
                new Expr.Column("o", "status"),
                Expr.Op.EQ,
                new Expr.Param("status")
            ),
            List.of(),
            null,
            List.of(
                new OrderItem(new Expr.Column("o", "created_at"), false),
                new OrderItem(new Expr.Column("o", "id"), true)
            ),
            new Paging(2, 10)
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.of("status", "PAID"));
        reporter.publishEntry("sql", rendered.sql());


        assertEquals(
            "SELECT \"o\".\"id\" AS \"order_id\", \"o\".\"status\" FROM \"sales\".\"orders\" \"o\" "
                + "WHERE \"o\".\"status\" = ? ORDER BY \"o\".\"created_at\" DESC, \"o\".\"id\" ASC "
                + "LIMIT ? OFFSET ? ",
            rendered.sql()
        );
        assertEquals(3, rendered.binds().size());
        assertEquals("PAID", ((Bind.Value) rendered.binds().get(0)).value());
        assertEquals(10, ((Bind.Value) rendered.binds().get(1)).value());
        assertEquals(10, ((Bind.Value) rendered.binds().get(2)).value());
    }

    @Test
    void renderJoinsGroupByHaving(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.Func("COUNT", List.of(new Expr.Column("i", "id"))), "item_count")),
            new TableRef("orders", "o"),
            List.of(
                new Join(
                    JoinType.JOIN,
                    new TableRef("customers", "c"),
                    new Expr.Compare(new Expr.Column("c", "id"), Expr.Op.EQ, new Expr.Column("o", "customer_id"))
                ),
                new Join(
                    JoinType.LEFT_JOIN,
                    new TableRef("items", "i"),
                    new Expr.Compare(new Expr.Column("i", "order_id"), Expr.Op.EQ, new Expr.Column("o", "id"))
                ),
                new Join(
                    JoinType.RIGHT_JOIN,
                    new TableRef("shipments", "s"),
                    new Expr.Compare(new Expr.Column("s", "order_id"), Expr.Op.EQ, new Expr.Column("o", "id"))
                )
            ),
            new Expr.Like(new Expr.Column("c", "name"), new Expr.Param("kw")),
            List.of(new Expr.Column("o", "id"), new Expr.Column("c", "name")),
            new Expr.Compare(new Expr.RawSql("item_count"), Expr.Op.GT, new Expr.Literal(1)),
            List.of(),
            null
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.of("kw", "%lamp%"));
        reporter.publishEntry("sql", rendered.sql());

        assertEquals(
            "SELECT COUNT(\"i\".\"id\") AS \"item_count\" FROM \"orders\" \"o\" "
                + "JOIN \"customers\" \"c\" ON \"c\".\"id\" = \"o\".\"customer_id\" "
                + "LEFT JOIN \"items\" \"i\" ON \"i\".\"order_id\" = \"o\".\"id\" "
                + "RIGHT JOIN \"shipments\" \"s\" ON \"s\".\"order_id\" = \"o\".\"id\" "
                + "WHERE \"c\".\"name\" LIKE ? GROUP BY \"o\".\"id\", \"c\".\"name\" "
                + "HAVING item_count > ?",
            rendered.sql()
        );
        assertEquals(2, rendered.binds().size());
        assertEquals("%lamp%", ((Bind.Value) rendered.binds().get(0)).value());
        assertEquals(1, ((Bind.Value) rendered.binds().get(1)).value());
    }

    @Test
    void renderLogicalAndInEmpty(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        Expr where = new Expr.And(List.of(
            new Expr.Not(new Expr.Or(List.of(new Expr.True(), new Expr.False()))),
            new Expr.In(new Expr.Column("o", "id"), List.of())
        ));
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            where,
            List.of(),
            null,
            List.of(),
            null
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.empty());
        reporter.publishEntry("sql", rendered.sql());

        assertEquals(
            "SELECT 1 FROM \"orders\" \"o\" WHERE (NOT ((1=1 OR 1=0)) AND 1=0)",
            rendered.sql()
        );
    }

    @Test
    void renderEmptyAndOrFallbacks(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        SelectStmt andStmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            new Expr.And(List.of()),
            List.of(),
            null,
            List.of(),
            null
        );
        SelectStmt orStmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            new Expr.Or(List.of()),
            List.of(),
            null,
            List.of(),
            null
        );

        RenderedSql andRendered = renderer.render(andStmt, Bindings.empty());
        RenderedSql orRendered = renderer.render(orStmt, Bindings.empty());
        reporter.publishEntry("sql", andRendered.sql());
        reporter.publishEntry("sql", orRendered.sql());
        assertEquals("SELECT 1 FROM \"orders\" \"o\" WHERE 1=1", andRendered.sql());
        assertEquals("SELECT 1 FROM \"orders\" \"o\" WHERE 1=0", orRendered.sql());
    }

    @Test
    void renderCompareOps(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        Expr where = new Expr.And(List.of(
            new Expr.Compare(new Expr.Column("o", "a"), Expr.Op.EQ, new Expr.Literal(1)),
            new Expr.Compare(new Expr.Column("o", "b"), Expr.Op.NE, new Expr.Literal(2)),
            new Expr.Compare(new Expr.Column("o", "c"), Expr.Op.GT, new Expr.Literal(3)),
            new Expr.Compare(new Expr.Column("o", "d"), Expr.Op.GE, new Expr.Literal(4)),
            new Expr.Compare(new Expr.Column("o", "e"), Expr.Op.LT, new Expr.Literal(5)),
            new Expr.Compare(new Expr.Column("o", "f"), Expr.Op.LE, new Expr.Literal(6))
        ));
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            where,
            List.of(),
            null,
            List.of(),
            null
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.empty());
        reporter.publishEntry("sql", rendered.sql());

        assertEquals(
            "SELECT 1 FROM \"orders\" \"o\" WHERE (\"o\".\"a\" = ? AND \"o\".\"b\" <> ? "
                + "AND \"o\".\"c\" > ? AND \"o\".\"d\" >= ? AND \"o\".\"e\" < ? AND \"o\".\"f\" <= ?)",
            rendered.sql()
        );
        assertEquals(6, rendered.binds().size());
    }

    @Test
    void renderMissingBindingThrows() {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            new Expr.Param("missing"),
            List.of(),
            null,
            List.of(),
            null
        );

        assertThrows(IllegalArgumentException.class, () -> renderer.render(stmt, Bindings.empty()));
    }

    @Test
    void renderLiteralNullUsesNullBind(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.RawSql("1"), null)),
            new TableRef("orders", "o"),
            List.of(),
            new Expr.Compare(new Expr.Column("o", "deleted_at"), Expr.Op.EQ, new Expr.Literal(null)),
            List.of(),
            null,
            List.of(),
            null
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.empty());
        reporter.publishEntry("sql", rendered.sql());

        assertEquals("SELECT 1 FROM \"orders\" \"o\" WHERE \"o\".\"deleted_at\" = ?", rendered.sql());
        assertEquals(1, rendered.binds().size());
        assertInstanceOf(Bind.NullValue.class, rendered.binds().get(0));
    }

    @Test
    void rendersInsertStatements(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        InsertStmt stmt = new InsertStmt(
            new TableRef("orders", null),
            List.of("id", "status"),
            List.of(List.of(new Expr.Param("id"), new Expr.Literal("NEW")))
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.of("id", 10));
        reporter.publishEntry("sql", rendered.sql());

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
    void rendersUpdateStatements(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        UpdateStmt stmt = new UpdateStmt(
            new TableRef("orders", null),
            List.of(new UpdateItem(new Expr.Column(null, "status"), new Expr.Param("status"))),
            new Expr.Compare(new Expr.Column(null, "id"), Expr.Op.EQ, new Expr.Param("id"))
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.of("status", "PAID", "id", 7));
        reporter.publishEntry("sql", rendered.sql());

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
    void rendersDeleteStatements(TestReporter reporter) {
        SqlRenderer renderer = new SqlRenderer(new LimitOffsetDialect("\""));
        DeleteStmt stmt = new DeleteStmt(
            new TableRef("orders", null),
            new Expr.Compare(new Expr.Column(null, "id"), Expr.Op.EQ, new Expr.Param("id"))
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.of("id", 5));
        reporter.publishEntry("sql", rendered.sql());

        assertEquals(
            "DELETE FROM \"orders\" WHERE \"id\" = ?",
            rendered.sql()
        );
        assertEquals(
            List.of(new Bind.Value(5, 0)),
            rendered.binds()
        );
    }

    @Test
    void renderFunctionsViaDialect(TestReporter reporter) {
        Dialect dialect = new Dialect() {
            @Override
            public String quoteIdent(String ident) {
                return "\"" + ident + "\"";
            }

            @Override
            public String id() {
                return "test";
            }

            @Override
            public RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy) {
                return new RenderedPagination("", List.of());
            }

            @Override
            public RenderedSql renderFunction(String name, List<RenderedSql> args) {
                RenderedSql arg = args.get(0);
                return new RenderedSql("COUNT(DISTINCT " + arg.sql() + ")", arg.binds());
            }
        };
        SqlRenderer renderer = new SqlRenderer(dialect);
        SelectStmt stmt = new SelectStmt(
            List.of(new SelectItem(new Expr.Func("count_distinct", List.of(new Expr.Column("o", "id"))), "cnt")),
            new TableRef("orders", "o"),
            List.of(),
            null,
            List.of(),
            null,
            List.of(),
            null
        );

        RenderedSql rendered = renderer.render(stmt, Bindings.empty());
        reporter.publishEntry("sql", rendered.sql());

        assertEquals(
            "SELECT COUNT(DISTINCT \"o\".\"id\") AS \"cnt\" FROM \"orders\" \"o\"",
            rendered.sql()
        );
        assertEquals(0, rendered.binds().size());
    }
}

package io.lighting.lumen.dsl;

import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.Join;
import io.lighting.lumen.sql.ast.JoinType;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class SelectBuilder {
    private final List<SelectItem> selectItems;

    SelectBuilder(List<SelectItem> selectItems) {
        this.selectItems = List.copyOf(selectItems);
    }

    public FromBuilder from(Table table) {
        Objects.requireNonNull(table, "table");
        return new FromBuilder(selectItems, table.ref());
    }

    public static final class FromBuilder {
        private final List<SelectItem> selectItems;
        private final TableRef from;
        private final List<Join> joins = new ArrayList<>();
        private Expr where;
        private final List<Expr> groupBy = new ArrayList<>();
        private Expr having;
        private final List<OrderItem> orderBy = new ArrayList<>();
        private Paging paging;

        private FromBuilder(List<SelectItem> selectItems, TableRef from) {
            this.selectItems = selectItems;
            this.from = from;
        }

        public JoinBuilder join(Table table) {
            return new JoinBuilder(this, JoinType.JOIN, table);
        }

        public JoinBuilder leftJoin(Table table) {
            return new JoinBuilder(this, JoinType.LEFT_JOIN, table);
        }

        public JoinBuilder rightJoin(Table table) {
            return new JoinBuilder(this, JoinType.RIGHT_JOIN, table);
        }

        public FromBuilder where(Expr expr) {
            this.where = Objects.requireNonNull(expr, "expr");
            return this;
        }

        public FromBuilder where(Consumer<PredicateBuilder> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            PredicateBuilder builder = PredicateBuilder.and();
            consumer.accept(builder);
            this.where = builder.build();
            return this;
        }

        public FromBuilder groupBy(Expr... expressions) {
            Objects.requireNonNull(expressions, "expressions");
            groupBy.clear();
            for (Expr expr : expressions) {
                groupBy.add(Objects.requireNonNull(expr, "expr"));
            }
            return this;
        }

        public FromBuilder having(Expr expr) {
            this.having = Objects.requireNonNull(expr, "expr");
            return this;
        }

        public FromBuilder having(Consumer<PredicateBuilder> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            PredicateBuilder builder = PredicateBuilder.and();
            consumer.accept(builder);
            this.having = builder.build();
            return this;
        }

        public FromBuilder orderBy(OrderItem... items) {
            Objects.requireNonNull(items, "items");
            orderBy.clear();
            for (OrderItem item : items) {
                orderBy.add(Objects.requireNonNull(item, "item"));
            }
            return this;
        }

        public FromBuilder orderBy(Consumer<OrderBuilder> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            OrderBuilder builder = new OrderBuilder();
            consumer.accept(builder);
            orderBy.clear();
            orderBy.addAll(builder.build());
            return this;
        }

        public FromBuilder page(int page, int pageSize) {
            this.paging = new Paging(page, pageSize);
            return this;
        }

        public FromBuilder page(PageRequest pageRequest) {
            Objects.requireNonNull(pageRequest, "pageRequest");
            return page(pageRequest.page(), pageRequest.pageSize());
        }

        public SelectStmt build() {
            return new SelectStmt(
                selectItems,
                from,
                List.copyOf(joins),
                where,
                List.copyOf(groupBy),
                having,
                List.copyOf(orderBy),
                paging
            );
        }

        private void addJoin(Join join) {
            joins.add(join);
        }
    }

    public static final class JoinBuilder {
        private final FromBuilder parent;
        private final JoinType type;
        private final TableRef table;

        private JoinBuilder(FromBuilder parent, JoinType type, Table table) {
            this.parent = Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(table, "table");
            this.type = Objects.requireNonNull(type, "type");
            this.table = table.ref();
        }

        public FromBuilder on(Expr on) {
            parent.addJoin(new Join(type, table, Objects.requireNonNull(on, "on")));
            return parent;
        }
    }
}

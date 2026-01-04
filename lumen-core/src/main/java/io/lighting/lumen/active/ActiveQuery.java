package io.lighting.lumen.active;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.ColumnRef;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.OrderBuilder;
import io.lighting.lumen.dsl.PredicateBuilder;
import io.lighting.lumen.dsl.PropertyNames;
import io.lighting.lumen.dsl.PropertyRef;
import io.lighting.lumen.dsl.SelectBuilder;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.meta.EntityMeta;
import io.lighting.lumen.meta.IdMeta;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDeleteMeta;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.JoinType;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ActiveQuery<T extends Model<T>> {
    private final Class<T> type;
    private final ActiveRecordConfig config;
    private final Dsl dsl;
    private final Table table;
    private final EntityMeta meta;
    private final List<SelectItem> selectItems = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<Expr> groupBy = new ArrayList<>();
    private final List<OrderItem> orderBy = new ArrayList<>();
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private PredicateBuilder whereBuilder;
    private Expr having;
    private Paging paging;
    private boolean selectSpecified;

    static <T extends Model<T>> ActiveQuery<T> of(Class<T> type) {
        return new ActiveQuery<>(type);
    }

    private ActiveQuery(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
        this.config = ActiveRecordSupport.requireConfig();
        this.dsl = config.dsl();
        this.table = dsl.table(type);
        this.meta = config.metaRegistry().metaOf(type);
    }

    public Table table() {
        return table;
    }

    public Table table(Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return dsl.table(entityType);
    }

    @SafeVarargs
    public final ActiveQuery<T> select(PropertyRef<T, ?>... refs) {
        Objects.requireNonNull(refs, "refs");
        if (refs.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        selectSpecified = true;
        for (PropertyRef<T, ?> ref : refs) {
            selectItems.add(table.col(ref).select());
        }
        return this;
    }

    public ActiveQuery<T> select(ColumnRef... columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        selectSpecified = true;
        for (ColumnRef column : columns) {
            selectItems.add(Objects.requireNonNull(column, "column").select());
        }
        return this;
    }

    public ActiveQuery<T> select(SelectItem... items) {
        Objects.requireNonNull(items, "items");
        if (items.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        selectSpecified = true;
        for (SelectItem item : items) {
            selectItems.add(Objects.requireNonNull(item, "item"));
        }
        return this;
    }

    public ActiveQuery<T> selectAll() {
        selectSpecified = true;
        selectItems.clear();
        for (String fieldName : meta.fieldToColumn().keySet()) {
            selectItems.add(table.col(fieldName).select());
        }
        return this;
    }

    public Condition where(PropertyRef<T, ?> ref) {
        Objects.requireNonNull(ref, "ref");
        return new Condition(table.col(ref));
    }

    public Condition where(ColumnRef column) {
        Objects.requireNonNull(column, "column");
        return new Condition(column);
    }

    public ActiveQuery<T> where(Expr expr) {
        addCondition(expr);
        return this;
    }

    public ActiveQuery<T> where(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        ensureWhereBuilder();
        consumer.accept(whereBuilder);
        return this;
    }

    public ActiveQuery<T> orGroup(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        ensureWhereBuilder();
        whereBuilder.orGroup(consumer);
        return this;
    }

    public ActiveQuery<T> orderBy(OrderItem... items) {
        Objects.requireNonNull(items, "items");
        orderBy.clear();
        for (OrderItem item : items) {
            orderBy.add(Objects.requireNonNull(item, "item"));
        }
        return this;
    }

    public ActiveQuery<T> orderBy(Consumer<OrderBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        OrderBuilder builder = new OrderBuilder();
        consumer.accept(builder);
        orderBy.clear();
        orderBy.addAll(builder.build());
        return this;
    }

    public ActiveQuery<T> groupBy(Expr... expressions) {
        Objects.requireNonNull(expressions, "expressions");
        groupBy.clear();
        for (Expr expr : expressions) {
            groupBy.add(Objects.requireNonNull(expr, "expr"));
        }
        return this;
    }

    public ActiveQuery<T> having(Expr expr) {
        this.having = Objects.requireNonNull(expr, "expr");
        return this;
    }

    public ActiveQuery<T> having(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PredicateBuilder builder = PredicateBuilder.and();
        consumer.accept(builder);
        this.having = builder.build();
        return this;
    }

    public ActiveQuery<T> paging(int page, int pageSize) {
        this.paging = new Paging(page, pageSize);
        return this;
    }

    public List<T> page(int page, int pageSize) throws SQLException {
        return paging(page, pageSize).objList();
    }

    public List<T> page(Page page) throws SQLException {
        Objects.requireNonNull(page, "page");
        return page(page.page(), page.pageSize());
    }

    public JoinStep join(Table joinTable) {
        return new JoinStep(JoinType.JOIN, joinTable);
    }

    public JoinStep leftJoin(Table joinTable) {
        return new JoinStep(JoinType.LEFT_JOIN, joinTable);
    }

    public JoinStep rightJoin(Table joinTable) {
        return new JoinStep(JoinType.RIGHT_JOIN, joinTable);
    }

    public ActiveQuery<T> set(PropertyRef<T, ?> ref, Object value) {
        Objects.requireNonNull(ref, "ref");
        return set(PropertyNames.name(ref), value);
    }

    public ActiveQuery<T> set(String fieldName, Object value) {
        Objects.requireNonNull(fieldName, "fieldName");
        if (!meta.fieldToColumn().containsKey(fieldName)) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        setValues.put(fieldName, value);
        return this;
    }

    public boolean update() throws SQLException {
        return updateRows() > 0;
    }

    public int updateRows() throws SQLException {
        ensureSetValues();
        Expr where = buildWhere();
        if (where == null) {
            throw new IllegalStateException("Update requires a where clause");
        }
        io.lighting.lumen.dsl.UpdateBuilder builder = dsl.update(table);
        for (Map.Entry<String, Object> entry : setValues.entrySet()) {
            builder.set(table.col(entry.getKey()), entry.getValue());
        }
        UpdateStmt stmt = builder.where(applyLogicalDelete(where)).build();
        RenderedSql rendered = config.renderer().render(stmt, Bindings.empty());
        return config.db().execute(Command.of(rendered));
    }

    public boolean remove() throws SQLException {
        return removeRows() > 0;
    }

    public int removeRows() throws SQLException {
        Expr where = buildWhere();
        if (where == null) {
            throw new IllegalStateException("Delete requires a where clause");
        }
        return executeDelete(where);
    }

    public boolean removeById() throws SQLException {
        return removeByIdRows() > 0;
    }

    public int removeByIdRows() throws SQLException {
        IdMeta idMeta = requireId();
        Object idValue = setValues.get(idMeta.fieldName());
        if (idValue == null) {
            throw new IllegalStateException("Missing id value for removeById");
        }
        Expr where = table.col(idMeta.fieldName()).eq(idValue);
        return executeDelete(where);
    }

    public boolean save() throws SQLException {
        return saveRows() > 0;
    }

    public int saveRows() throws SQLException {
        ensureSetValues();
        if (whereBuilder != null) {
            throw new IllegalStateException("Save does not support where clauses");
        }
        IdMeta idMeta = meta.idMeta().orElse(null);
        if (idMeta != null && idMeta.strategy() != IdStrategy.AUTO) {
            Object current = setValues.get(idMeta.fieldName());
            if (!hasIdValue(idMeta, current)) {
                Object generated = config.idGenerator().generate(type).orElse(null);
                if (generated != null) {
                    setValues.put(idMeta.fieldName(), generated);
                }
            }
        }
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        if (logicDeleteMeta != null && !setValues.containsKey(logicDeleteMeta.fieldName())) {
            setValues.put(logicDeleteMeta.fieldName(), logicDeleteMeta.activeValue());
        }
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> entry : setValues.entrySet()) {
            if (idMeta != null && entry.getKey().equals(idMeta.fieldName())
                && idMeta.strategy() == IdStrategy.AUTO && entry.getValue() == null) {
                continue;
            }
            columns.add(meta.columnForField(entry.getKey()));
            values.add(entry.getValue());
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("No insertable values");
        }
        RenderedSql rendered = config.renderer().render(
            dsl.insertInto(table)
                .columns(columns.toArray(new String[0]))
                .row(values.toArray())
                .build(),
            Bindings.empty()
        );
        return config.db().execute(Command.of(rendered));
    }

    public T one() throws SQLException {
        List<T> results = objList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<T> objList() throws SQLException {
        RenderedSql rendered = config.renderer().render(buildSelect(), Bindings.empty());
        return config.db().fetch(Query.of(rendered), type);
    }

    private void ensureWhereBuilder() {
        if (whereBuilder == null) {
            whereBuilder = PredicateBuilder.and();
        }
    }

    private void addCondition(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        ensureWhereBuilder();
        whereBuilder.and(expr);
    }

    private Expr buildWhere() {
        return whereBuilder == null ? null : whereBuilder.build();
    }

    private SelectStmt buildSelect() {
        List<SelectItem> items = new ArrayList<>();
        if (!selectSpecified) {
            for (String fieldName : meta.fieldToColumn().keySet()) {
                items.add(table.col(fieldName).select());
            }
        } else {
            items.addAll(selectItems);
        }
        SelectBuilder.FromBuilder from = dsl.select(items.toArray(new SelectItem[0])).from(table);
        for (JoinSpec join : joins) {
            SelectBuilder.JoinBuilder joinBuilder = switch (join.type) {
                case JOIN -> from.join(join.table);
                case LEFT_JOIN -> from.leftJoin(join.table);
                case RIGHT_JOIN -> from.rightJoin(join.table);
            };
            joinBuilder.on(join.on);
        }
        Expr where = buildWhere();
        if (where != null || config.filterLogicalDelete()) {
            Expr withFilter = applyLogicalDelete(where);
            if (withFilter != null) {
                from.where(withFilter);
            }
        }
        if (!groupBy.isEmpty()) {
            from.groupBy(groupBy.toArray(new Expr[0]));
        }
        if (having != null) {
            from.having(having);
        }
        if (!orderBy.isEmpty()) {
            from.orderBy(orderBy.toArray(new OrderItem[0]));
        }
        if (paging != null) {
            from.page(paging.page(), paging.pageSize());
        }
        return from.build();
    }

    private void ensureSetValues() {
        if (setValues.isEmpty()) {
            throw new IllegalStateException("No values set");
        }
    }

    private Expr applyLogicalDelete(Expr where) {
        if (config.filterLogicalDelete() && meta.logicDeleteMeta().isPresent()) {
            if (where == null) {
                return table.notDeleted();
            }
            return new Expr.And(List.of(where, table.notDeleted()));
        }
        return where;
    }

    private int executeDelete(Expr where) throws SQLException {
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        RenderedSql rendered;
        if (logicDeleteMeta != null) {
            rendered = config.renderer().render(
                dsl.logicalDeleteFrom(table).where(applyLogicalDelete(where)).build(),
                Bindings.empty()
            );
        } else {
            rendered = config.renderer().render(
                dsl.deleteFrom(table).where(where).build(),
                Bindings.empty()
            );
        }
        return config.db().execute(Command.of(rendered));
    }

    private IdMeta requireId() {
        return meta.idMeta()
            .orElseThrow(() -> new IllegalArgumentException("Missing @Id on " + type.getName()));
    }

    private boolean hasIdValue(IdMeta idMeta, Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0L || idMeta.strategy() != IdStrategy.AUTO;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private record JoinSpec(JoinType type, Table table, Expr on) {
        private JoinSpec {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(table, "table");
            Objects.requireNonNull(on, "on");
        }
    }

    public final class JoinStep {
        private final JoinType type;
        private final Table table;

        private JoinStep(JoinType type, Table table) {
            this.type = Objects.requireNonNull(type, "type");
            this.table = Objects.requireNonNull(table, "table");
        }

        public ActiveQuery<T> on(Expr expr) {
            Objects.requireNonNull(expr, "expr");
            joins.add(new JoinSpec(type, table, expr));
            return ActiveQuery.this;
        }
    }

    public final class Condition {
        private final ColumnRef column;

        private Condition(ColumnRef column) {
            this.column = Objects.requireNonNull(column, "column");
        }

        public ActiveQuery<T> eq(Object value) {
            return add(column.eq(value));
        }

        public ActiveQuery<T> ne(Object value) {
            return add(column.ne(value));
        }

        public ActiveQuery<T> gt(Object value) {
            return add(column.gt(value));
        }

        public ActiveQuery<T> ge(Object value) {
            return add(column.ge(value));
        }

        public ActiveQuery<T> lt(Object value) {
            return add(column.lt(value));
        }

        public ActiveQuery<T> le(Object value) {
            return add(column.le(value));
        }

        public ActiveQuery<T> like(Object pattern) {
            return add(column.like(pattern));
        }

        public ActiveQuery<T> in(Iterable<?> values) {
            return add(column.in(values));
        }

        public ActiveQuery<T> in(Object... values) {
            return add(column.in(values));
        }

        public ActiveQuery<T> isNull() {
            return add(column.isNull());
        }

        public ActiveQuery<T> isNotNull() {
            return add(column.isNotNull());
        }

        private ActiveQuery<T> add(Expr expr) {
            addCondition(expr);
            return ActiveQuery.this;
        }
    }
}

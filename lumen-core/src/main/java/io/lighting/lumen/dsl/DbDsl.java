package io.lighting.lumen.dsl;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 轻量级 DSL 入口，围绕数据库操作构建类型安全的查询步骤。
 * <p>
 * 该 DSL 以「步骤链」的形式暴露 select/from/where/orderBy/page 等常用能力，
 * 同时将实体属性引用（{@link PropertyRef}）映射为列信息，以保证编译期安全性。
 * <p>
 * 设计目标：
 * <ul>
 *     <li>对用户暴露尽量少的细节，隐藏 SQL AST 与渲染细节。</li>
 *     <li>基于实体属性引用进行列选择与条件构建，避免硬编码字符串。</li>
 *     <li>保持链式调用语义清晰，步骤不完整时无法执行查询。</li>
 * </ul>
 */
public final class DbDsl {
    private final Db db;
    private final Dsl dsl;

    /**
     * 创建 DSL 实例。
     *
     * @param db           底层数据库访问入口
     * @param metaRegistry 实体元数据注册表，用于解析表与列信息
     */
    public DbDsl(Db db, EntityMetaRegistry metaRegistry) {
        this.db = Objects.requireNonNull(db, "db");
        Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.dsl = new Dsl(metaRegistry);
    }

    /**
     * 以目标结果类型与列引用启动查询。
     * <p>
     * 返回的 {@link SelectStep} 会保存结果类型与列清单，
     * 后续必须调用 {@link SelectStep#from(Class)} 指定实体表。
     *
     * @param resultType 结果类型（例如 DTO/Record）
     * @param columns    列引用，不能为空
     * @param <T>        结果类型
     * @param <E>        实体类型（表所属的实体类）
     * @return select 步骤对象
     */
    public <T, E> SelectStep<T, E> select(Class<T> resultType, PropertyRef<E, ?>... columns) {
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(columns, "columns");
        if (columns.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        return new SelectStep<>(db, dsl, resultType, List.of(columns));
    }

    /**
     * select 阶段，保存结果类型与列清单，等待指定 from 表。
     * <p>
     * 该步骤不直接执行查询；它仅负责将列引用转换为可渲染的 {@link SelectItem}。
     *
     * @param <T> 结果类型
     * @param <E> 实体类型
     */
    public static final class SelectStep<T, E> {
        private final Db db;
        private final Dsl dsl;
        private final Class<T> resultType;
        private final List<PropertyRef<E, ?>> columns;

        /**
         * 构造 select 步骤。
         *
         * @param db         数据库入口
         * @param dsl        DSL 内核实例
         * @param resultType 结果类型
         * @param columns    列引用列表
         */
        private SelectStep(Db db, Dsl dsl, Class<T> resultType, List<PropertyRef<E, ?>> columns) {
            this.db = db;
            this.dsl = dsl;
            this.resultType = resultType;
            this.columns = columns;
        }

        /**
         * 指定查询表（实体类型）。
         * <p>
         * 该方法会将列引用映射为具体列，并根据结果类型的字段信息决定别名，
         * 以便行映射时可以正确匹配字段或构造参数。
         *
         * @param entityType 实体类型
         * @return from 步骤对象
         */
        public FromStep<T, E> from(Class<E> entityType) {
            Objects.requireNonNull(entityType, "entityType");
            Table table = dsl.table(entityType);
            List<SelectItem> items = new ArrayList<>(columns.size());
            for (PropertyRef<E, ?> ref : columns) {
                String propertyName = PropertyNames.name(ref);
                String alias = resolveAlias(resultType, propertyName);
                items.add(table.col(ref).as(alias));
            }
            return new FromStep<>(db, dsl, resultType, table, items);
        }
    }

    /**
     * from 阶段：已经确定 select 列与来源表，可继续构建 where/orderBy/page 或直接执行。
     *
     * @param <T> 结果类型
     * @param <E> 实体类型
     */
    public static final class FromStep<T, E> {
        private final Db db;
        private final Dsl dsl;
        private final Class<T> resultType;
        private final Table table;
        private final List<SelectItem> selectItems;
        private final List<OrderItem> orderBy = new ArrayList<>();
        private Expr where;
        private Paging paging;

        /**
         * 构造 from 步骤。
         *
         * @param db          数据库入口
         * @param dsl         DSL 内核实例
         * @param resultType  结果类型
         * @param table       目标表
         * @param selectItems 选择列
         */
        private FromStep(
            Db db,
            Dsl dsl,
            Class<T> resultType,
            Table table,
            List<SelectItem> selectItems
        ) {
            this.db = db;
            this.dsl = dsl;
            this.resultType = resultType;
            this.table = table;
            this.selectItems = List.copyOf(selectItems);
        }

        /**
         * 进入 where 条件构建阶段。
         *
         * @return where 步骤对象
         */
        public WhereStep<T, E> where() {
            return new WhereStep<>(this);
        }

        /**
         * 设置升序排序列。
         *
         * @param refs 列引用
         * @return 当前步骤对象
         */
        public FromStep<T, E> orderBy(PropertyRef<E, ?>... refs) {
            return applyOrderBy(true, refs);
        }

        /**
         * 设置降序排序列。
         *
         * @param refs 列引用
         * @return 当前步骤对象
         */
        public FromStep<T, E> orderByDesc(PropertyRef<E, ?>... refs) {
            return applyOrderBy(false, refs);
        }

        /**
         * 设置分页参数（页码与页大小）。
         *
         * @param page     页码（从 1 开始）
         * @param pageSize 页大小
         * @return 当前步骤对象
         */
        public FromStep<T, E> page(int page, int pageSize) {
            this.paging = new Paging(page, pageSize);
            return this;
        }

        /**
         * 执行查询并返回结果列表，使用自动行映射器。
         *
         * @return 结果列表
         * @throws SQLException 数据库访问异常
         */
        public List<T> toList() throws SQLException {
            return toList(RowMappers.auto(resultType));
        }

        /**
         * 执行查询并返回结果列表，使用自定义行映射器。
         *
         * @param mapper 行映射器
         * @return 结果列表
         * @throws SQLException 数据库访问异常
         */
        public List<T> toList(RowMapper<T> mapper) throws SQLException {
            Objects.requireNonNull(mapper, "mapper");
            SelectStmt stmt = buildStmt();
            return db.fetch(Query.of(stmt, Bindings.empty()), mapper);
        }

        /**
         * 应用排序规则。
         *
         * @param asc  升序/降序标记
         * @param refs 列引用
         * @return 当前步骤对象
         */
        private FromStep<T, E> applyOrderBy(boolean asc, PropertyRef<E, ?>... refs) {
            Objects.requireNonNull(refs, "refs");
            if (refs.length == 0) {
                throw new IllegalArgumentException("Order by list must not be empty");
            }
            orderBy.clear();
            for (PropertyRef<E, ?> ref : refs) {
                ColumnRef column = table.col(ref);
                orderBy.add(asc ? column.asc() : column.desc());
            }
            return this;
        }

        /**
         * 设置 where 条件，供 where 步骤调用。
         *
         * @param expr 条件表达式
         */
        private void setWhere(Expr expr) {
            this.where = expr;
        }

        /**
         * 构建最终的 select 语句 AST。
         *
         * @return select 语句
         */
        private SelectStmt buildStmt() {
            return new SelectStmt(
                selectItems,
                table.ref(),
                List.of(),
                where,
                List.of(),
                null,
                List.copyOf(orderBy),
                paging
            );
        }
    }

    /**
     * where 阶段：提供条件构建能力，并最终执行查询。
     *
     * @param <T> 结果类型
     * @param <E> 实体类型
     */
    public static final class WhereStep<T, E> extends ConditionBase<WhereStep<T, E>, E> {
        private final FromStep<T, E> parent;

        /**
         * 构造 where 步骤。
         *
         * @param parent 上一步 from
         */
        private WhereStep(FromStep<T, E> parent) {
            super(parent.table);
            this.parent = parent;
        }

        /**
         * 生成 where 条件并执行查询。
         *
         * @return 结果列表
         * @throws SQLException 数据库访问异常
         */
        public List<T> toList() throws SQLException {
            parent.setWhere(build());
            return parent.toList();
        }

        /**
         * 生成 where 条件并执行查询，使用自定义行映射器。
         *
         * @param mapper 行映射器
         * @return 结果列表
         * @throws SQLException 数据库访问异常
         */
        public List<T> toList(RowMapper<T> mapper) throws SQLException {
            parent.setWhere(build());
            return parent.toList(mapper);
        }

        @Override
        protected WhereStep<T, E> self() {
            return this;
        }
    }

    /**
     * 条件构建基础类，封装通用的比较与逻辑组合操作。
     *
     * @param <T> 具体子类类型（用于链式返回）
     * @param <E> 实体类型
     */
    private abstract static class ConditionBase<T extends ConditionBase<T, E>, E> {
        private final Table table;
        private final PredicateBuilder predicate;
        private LogicalOp nextOp = LogicalOp.AND;

        /**
         * 构造条件构建器。
         *
         * @param table 目标表
         */
        protected ConditionBase(Table table) {
            this.table = Objects.requireNonNull(table, "table");
            this.predicate = PredicateBuilder.and();
        }

        /**
         * 返回当前实例（用于链式调用）。
         *
         * @return 当前实例
         */
        protected abstract T self();

        /**
         * 等于条件。
         */
        public T equals(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.EQ, value);
        }

        /**
         * 不等于条件。
         */
        public T notEquals(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.NE, value);
        }

        /**
         * 大于条件。
         */
        public T greaterThan(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.GT, value);
        }

        /**
         * 大于等于条件。
         */
        public T greaterOrEqual(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.GE, value);
        }

        /**
         * 小于条件。
         */
        public T lessThan(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.LT, value);
        }

        /**
         * 小于等于条件。
         */
        public T lessOrEqual(PropertyRef<E, ?> ref, Object value) {
            return addCompare(ref, Expr.Op.LE, value);
        }

        /**
         * like 条件。
         */
        public T like(PropertyRef<E, ?> ref, Object value) {
            Objects.requireNonNull(ref, "ref");
            add(table.col(ref).like(guardValue(value)));
            return self();
        }

        /**
         * in 条件（Iterable）。
         */
        public T in(PropertyRef<E, ?> ref, Iterable<?> values) {
            Objects.requireNonNull(ref, "ref");
            add(table.col(ref).in(values));
            return self();
        }

        /**
         * in 条件（可变参数）。
         */
        public T in(PropertyRef<E, ?> ref, Object... values) {
            Objects.requireNonNull(ref, "ref");
            add(table.col(ref).in(values));
            return self();
        }

        /**
         * is null 条件。
         */
        public T isNull(PropertyRef<E, ?> ref) {
            Objects.requireNonNull(ref, "ref");
            add(table.col(ref).isNull());
            return self();
        }

        /**
         * is not null 条件。
         */
        public T isNotNull(PropertyRef<E, ?> ref) {
            Objects.requireNonNull(ref, "ref");
            add(table.col(ref).isNotNull());
            return self();
        }

        /**
         * 指定下一条件与当前条件之间的逻辑为 AND。
         */
        public T and() {
            nextOp = LogicalOp.AND;
            return self();
        }

        /**
         * 指定下一条件与当前条件之间的逻辑为 OR。
         */
        public T or() {
            nextOp = LogicalOp.OR;
            return self();
        }

        /**
         * AND 条件分组。
         */
        public T andGroup(Consumer<ConditionGroup<E>> consumer) {
            return addGroup(consumer, LogicalOp.AND);
        }

        /**
         * OR 条件分组。
         */
        public T orGroup(Consumer<ConditionGroup<E>> consumer) {
            return addGroup(consumer, LogicalOp.OR);
        }

        /**
         * 构建最终 where 表达式。
         *
         * @return 表达式 AST
         */
        protected Expr build() {
            return predicate.build();
        }

        /**
         * 添加比较表达式。
         */
        private T addCompare(PropertyRef<E, ?> ref, Expr.Op op, Object value) {
            Objects.requireNonNull(ref, "ref");
            Expr expr = new Expr.Compare(table.col(ref).expr(), op, toExpr(guardValue(value)));
            add(expr);
            return self();
        }

        /**
         * 将值转换为表达式。
         */
        private Expr toExpr(Object value) {
            if (value instanceof Expr expr) {
                return expr;
            }
            return new Expr.Literal(value);
        }

        /**
         * 校验参数，屏蔽命名参数。
         */
        private Object guardValue(Object value) {
            if (value instanceof Expr.Param) {
                throw new IllegalArgumentException("Named parameters are not supported in this DSL");
            }
            return value;
        }

        /**
         * 添加条件分组。
         */
        private T addGroup(Consumer<ConditionGroup<E>> consumer, LogicalOp op) {
            Objects.requireNonNull(consumer, "consumer");
            ConditionGroup<E> group = new ConditionGroup<>(table);
            consumer.accept(group);
            Expr built = group.build();
            if (built != null) {
                addWithOp(built, op);
            }
            return self();
        }

        /**
         * 以当前逻辑运算符追加表达式。
         */
        private void add(Expr expr) {
            addWithOp(expr, nextOp);
        }

        /**
         * 按指定逻辑运算符追加表达式，并重置下一逻辑为 AND。
         */
        private void addWithOp(Expr expr, LogicalOp op) {
            if (op == LogicalOp.AND) {
                predicate.and(expr);
            } else {
                predicate.or(expr);
            }
            nextOp = LogicalOp.AND;
        }
    }

    /**
     * 条件分组，用于构建括号内的逻辑表达式。
     *
     * @param <E> 实体类型
     */
    private static final class ConditionGroup<E> extends ConditionBase<ConditionGroup<E>, E> {
        /**
         * 构造条件分组。
         *
         * @param table 目标表
         */
        private ConditionGroup(Table table) {
            super(table);
        }

        @Override
        protected ConditionGroup<E> self() {
            return this;
        }
    }

    /**
     * 逻辑运算符枚举。
     */
    private enum LogicalOp {
        AND,
        OR
    }

    /**
     * 根据结果类型与属性名解析列别名。
     * <p>
     * 解析规则：
     * <ul>
     *     <li>优先查找结果类型的同名字段。</li>
     *     <li>若字段存在且标注 {@link Column} 且 name 非空，则使用该 name。</li>
     *     <li>否则回退为属性名。</li>
     * </ul>
     *
     * @param resultType   结果类型
     * @param propertyName 属性名
     * @return 选择项别名
     */
    private static String resolveAlias(Class<?> resultType, String propertyName) {
        Field field = findField(resultType, propertyName);
        if (field == null) {
            return propertyName;
        }
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return column.name();
        }
        return propertyName;
    }

    /**
     * 从类层级中查找指定字段。
     *
     * @param type 类型
     * @param name 字段名
     * @return 字段（可能为 null）
     */
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try next superclass
            }
        }
        return null;
    }
}

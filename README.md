# 自研 Java SQL-First ORM/查询框架设计文档（研究型）

**版本**：v0.9（提案）
**目标读者**：Java 平台架构师、基础设施工程师、数据访问层开发者
**语言与运行时**：Java 17+（Text Blocks）；可选 Java 21+ APT 适配；
**外部依赖**：不依赖任何第三方 ORM（允许可选使用 JDBC 驱动与连接池，但核心不绑定）

---

## 摘要（Abstract）

本文提出一种面向 Java 平台的 SQL-First 数据访问框架设计：在不依赖第三方 ORM 的前提下，同时提供两种互补的查询构建入口：

1. **Text Block + 指令式模板（@if/@for/@where 等）**，使 SQL 在 Java 代码中保持“原生 SQL 的可读性”，并支持动态条件/循环片段拼装；
2. **类 SQL Fluent DSL（.select/.from/.leftJoin/.where 等）**，提供结构化、可组合的查询表达能力。

两种入口统一编译为同一套 **SQL AST（抽象语法树）**，经由 **Renderer** 输出参数化 SQL 与绑定序列，最终由 **JDBC Executor** 执行。框架提供 **Entity-first 元模型**（反射缓存或 APT 生成），并将“方言差异”严格限制在 **分页与标识符引用/少量 entity 语义生成**，以控制复杂度与长期维护成本。本文同时给出编译期校验策略（APT）与可选编译器插件策略，实现接近“编译器检查”的开发体验，并在安全模型（值绑定 vs 标识符注入）上给出形式化约束。

---

## 1. 背景与动机

在 Java 生态中，MyBatis/JPA 等方案要么偏向 XML/注解脚本式动态 SQL，要么偏向实体状态机式 ORM。本文关注的目标是：

* 保持 SQL 的原生表达力与可调试性（SQL-first）；
* 同时提供现代化的组合能力（DSL-first）；
* 尽量在编译期暴露错误并提供强约束的安全边界；
* 最小化方言适配面，避免“无止境的数据库差异治理”拖垮框架迭代。

---

## 2. 设计目标与非目标

### 2.1 设计目标（Goals）

G1. **双入口统一语义**：模板与 DSL 均编译为同一 SQL AST，确保能力一致、行为可预测。
G2. **值绑定安全默认**：所有用户输入均以 PreparedStatement 绑定参数传递，禁止默认字符串插值。
G3. **标识符安全白名单**：表名/列名/排序字段必须来自元模型，不允许任意字符串注入。
G4. **可选编译期检查**：

* `@SqlTemplate("""...""")`：APT 强校验、生成执行代码；
* `.run("""...""")`：默认运行时解析；可通过 `@SqlConst` 或编译器插件增强到编译期。
  G5. **方言最小化**：Dialect 仅覆盖分页语法、标识符 quoting、以及少量 entity 生成差异。
  G6. **可观测性**：统一输出结构化 SQL、绑定参数、耗时、调用点（可插拔）。
  G7. **低侵入易集成**：与 Spring 等框架可集成，但核心不强依赖。

### 2.2 非目标（Non-Goals）

N1. 不实现 Hibernate 式实体状态机（脏检查、一级缓存语义、级联持久化等）。
N2. 不实现通用 SQL 方言翻译器（JSON 函数差异、复杂 upsert/merge 等不纳入 Dialect）。
N3. 不强制代码生成；APT 为可选增强路径。

---

## 3. 总体架构

### 3.1 模块分层

```
┌──────────────────────────────────────────────────────────────┐
│                      Application Code                         │
│  (A) @SqlTemplate Text Block     (B) Fluent DSL               │
└───────────────┬──────────────────────┬───────────────────────┘
                │                      │
                ▼                      ▼
        Template Compiler         DSL Builder
      (parse + expand)         (typed AST build)
                └──────────────┬──────────────┘
                               ▼
                         SQL AST Model
            (Select/Join/Where/Expr/Order/Paging...)
                               ▼
                           Renderer
          (SQL string + binds + diagnostics + hooks)
                               ▼
                           Executor
     (JDBC PreparedStatement + mapping + transactions)
                               ▼
                         Database Driver
```

### 3.2 两条入口的定位

* **模板入口**：优先可读性与接近原生 SQL，适合复杂查询“按 SQL 形式思考”；动态通过指令表达；支持 entity-first 宏。
* **DSL 入口**：优先结构化组合与重用（可将条件、join 片段作为函数组合），适合复杂业务的可维护性治理。

---

## 4. 核心数据结构：SQL AST 与绑定模型

### 4.1 Render 输出模型（核心契约）

```java
public record RenderedSql(String sql, List<Bind> binds) {}

public sealed interface Bind permits Bind.Value, Bind.NullValue {
  int jdbcType(); // java.sql.Types or 0 unknown
  record Value(Object value, int jdbcType) implements Bind {}
  record NullValue(int jdbcType) implements Bind {}
}
```

约束：

* SQL 中只允许 `?` 占位符；绑定顺序严格按生成顺序。
* 不允许默认字符串插值进入 SQL（除非显式 `RawSql`，且需经过安全审计）。

### 4.2 AST 节点（最小可用集合）

```java
// Statement
public sealed interface Stmt permits SelectStmt, InsertStmt, UpdateStmt, DeleteStmt {}

public record SelectStmt(
    List<SelectItem> select,
    TableRef from,
    List<Join> joins,
    Expr where,
    List<Expr> groupBy,
    Expr having,
    List<OrderItem> orderBy,
    Paging paging
) implements Stmt {}

public record InsertStmt(
    TableRef table,
    List<String> columns,
    List<List<Expr>> rows
) implements Stmt {}

public record UpdateStmt(
    TableRef table,
    List<UpdateItem> assignments,
    Expr where
) implements Stmt {}

public record UpdateItem(Expr.Column column, Expr value) {}

public record DeleteStmt(
    TableRef table,
    Expr where
) implements Stmt {}

// Table & Join
public record TableRef(String tableName, String alias) {}
public record Join(JoinType type, TableRef table, Expr on) {}
public enum JoinType { JOIN, LEFT_JOIN, RIGHT_JOIN }

// Select
public record SelectItem(Expr expr, String alias) {}

// Ordering / Paging
public record OrderItem(Expr expr, boolean asc) {}
public record Paging(int page, int pageSize) {} // 语义层，渲染由 Dialect 决定

// Expressions
public sealed interface Expr permits
    Expr.And, Expr.Or, Expr.Not,
    Expr.Compare, Expr.In, Expr.Like,
    Expr.Func, Expr.Param, Expr.Column, Expr.Literal, Expr.RawSql, Expr.True, Expr.False {
  record And(List<Expr> items) implements Expr {}
  record Or(List<Expr> items) implements Expr {}
  record Not(Expr item) implements Expr {}
  record Compare(Expr left, Op op, Expr right) implements Expr {}
  record In(Expr left, List<Expr> rights) implements Expr {}
  record Like(Expr left, Expr pattern) implements Expr {}
  record Func(String name, List<Expr> args) implements Expr {}
  record Param(String name) implements Expr {} // 命名参数，渲染时绑定
  record Column(String tableAlias, String columnName) implements Expr {}
  record Literal(Object value) implements Expr {}
  record RawSql(String sqlFragment) implements Expr {} // 高危：需显式开启/审计
  record True() implements Expr {}
  record False() implements Expr {}
  enum Op { EQ, NE, GT, GE, LT, LE }
}
```

---

## 5. Entity-first 元模型（Meta Model）

### 5.1 注解定义（示例）

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table { String name(); }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column { String name(); }

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
  IdStrategy strategy() default IdStrategy.AUTO;
}

public enum IdStrategy {
  AUTO, UUID, SNOWFLAKE
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogicDelete {
  String active() default "0";
  String deleted() default "1";
}
```

### 5.2 运行时反射缓存（MVP）

```java
public final class EntityMeta {
  public final String table;
  public final Map<String, String> fieldToColumn;
  public final Set<String> columns;
  public final Optional<IdMeta> idMeta;
  public final Optional<LogicDeleteMeta> logicDeleteMeta;
  // ...
}

public interface EntityMetaRegistry {
  EntityMeta metaOf(Class<?> entityType);
}
```

### 5.3 APT 生成元模型（增强路径）

APT 生成 `QOrder`, `QProduct` 等：

```java
public final class QOrder {
  public static final QOrder ORDER = new QOrder("orders", "o");
  private final String table; private final String alias;

  public QOrder as(String alias) { return new QOrder(this.table, alias); }

  public ColumnRef id() { return ColumnRef.of(alias, "id"); }
  public ColumnRef orderNo() { return ColumnRef.of(alias, "order_no"); }
  public ColumnRef createdAt() { return ColumnRef.of(alias, "created_at"); }
  // ...
}
```

这样 DSL 端的列引用天然重构安全。

当前实现约定：

* 处理所有标注 `@Table` 的类型；
* 仅输出标注 `@Column` / `@Id` / `@LogicDelete` 的字段；
* 生成方法返回 `ColumnRef.of(alias, "column")`，可直接用于 DSL/AST。

### 5.4 ID 策略与逻辑删除

ID 策略：

```java
@Table(name = "orders")
class Order {
  @Id(strategy = IdStrategy.SNOWFLAKE)
  private long id;
}

EntityIdGenerator ids = new EntityIdGenerator(metaRegistry);
Object nextId = ids.generate(Order.class).orElse(null); // AUTO 时为空
```

逻辑删除：

```java
@Table(name = "orders")
class Order {
  @LogicDelete(active = "0", deleted = "1")
  private int deleted;
}

var o = dsl.table(Order.class).as("o");
UpdateStmt logicalDelete = dsl.logicalDeleteFrom(o)
    .where(o.col(Order::id).eq(param("id")))
    .build();

Expr onlyActive = o.notDeleted();
```

---

## 6. 方言（Dialect）最小化设计

### 6.1 Dialect 接口

```java
public interface Dialect {
  String id();
  String quoteIdent(String ident);

  /**
   * 渲染分页（page 从 1 开始）。
   * 若数据库要求 ORDER BY 才允许 OFFSET/FETCH，应在渲染阶段校验并报错。
   */
  RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy);

  default RenderedSql renderFunction(String name, List<RenderedSql> args) { ... }
}

public record RenderedPagination(String sqlFragment, List<Bind> binds) {}
```

### 6.2 示例实现（MySQL/Postgres）

```java
public final class LimitOffsetDialect implements Dialect {
  @Override public String id() { return "mysql"; }
  @Override public String quoteIdent(String ident) { return "`" + ident + "`"; } // MySQL 示例

  @Override
  public RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy) {
    int offset = Math.max(0, (page - 1) * pageSize);
    return new RenderedPagination(" LIMIT ? OFFSET ? ",
        List.of(new Bind.Value(pageSize, 0), new Bind.Value(offset, 0)));
  }
}
```

> 说明：SQL Server/Oracle 等可另写方言；函数差异由 `renderFunction` 处理（如需覆盖）。

默认情况下，`Lumen.builder()` 会基于 `DataSource` 自动识别方言（内部使用 `DialectResolver`）。
如需手动构建，可直接调用 `DialectResolver.resolve(dataSource)` 获取默认方言实例。

---

## 7. 模板语言：Text Block + 指令（@if/@for/...）

### 7.1 语法概览（建议的最小集合）

* `@if(<expr>) { ... }`
* `@for(x : <expr>) { ... }`
* `@where { ... }`：自动处理空块与 `AND/OR` 前缀
* `@having { ... }`
* `@or { ... }`：插入一个 OR 子片段（常用于循环组合）
* `@in(:param)`：安全展开 IN 列表（空集合策略可配置：`NULL`/`FALSE`/`ERROR`）
* `@table(Entity)`：输出表名（来自元模型）
* `@col(Entity::field)`：输出列名（来自元模型）
* `@orderBy(:sort, allowed={...}, default=...)`：排序白名单
* `@page(:page, :pageSize)`：分页语义节点（交给 Dialect 渲染）

### 7.2 语义约束（安全模型）

* 模板中任何 `:name` 视为 **值参数**，必须绑定为 `?` 并进入 binds。
* 表名/列名/排序片段只能来自 `@table/@col/@orderBy.allowed`，禁止 `:param` 直接出现在标识符位置。
* `RawSql` 需显式启用，并要求审计 hook（默认禁用）。
* `@in` 空集合策略通过 `EmptyInStrategy` 配置（默认 `NULL`，可改为 `FALSE`/`ERROR`）。

### 7.3 系统内置变量（条件编译）

提供 `::dialect` 作为系统只读变量，用于按方言选择 SQL 片段：

```sql
SELECT * FROM orders
@if(::dialect == 'mysql') { LIMIT 1, 1 }
@if(::dialect == 'oracle') { ROWNUM <= 1 }
```

`::dialect` 的值来自 `Dialect.id()`，默认由方言实现决定。

### 7.3 模板编译策略（原理）

模板不是“字符串宏替换器”，而是编译为 AST：

1. **Tokenize**：识别 SQL 文本片段、`:param`、`@directive(...)`、`{}` 块。
2. **Build Template AST**：节点包括 `TextNode`、`ParamNode`、`IfNode`、`ForNode`、`WhereBlock` 等。
3. **Expand**：在给定 `BindContext`（参数值、元模型、排序选择、方言）下展开成 SQL AST 或 QueryPart 列表。
4. **Render**：最终由 Renderer 输出 `RenderedSql`。

---

## 8. Fluent DSL：类 SQL Builder 设计

### 8.1 目标

* 结构化：避免括号/AND/逗号错误；
* 可组合：where/join 片段可作为函数返回；
* 与模板共享元模型引用，函数直接写 SQL 或用 `Dsl.function(...)` 构造。

### 8.2 DSL API（示意）

```java
import static io.lighting.lumen.dsl.Dsl.function;
import static io.lighting.lumen.dsl.Dsl.literal;

var o = QOrder.ORDER.as("o");
var m = QMerchant.MERCHANT.as("m");
var oi = QOrderItem.ORDER_ITEM.as("oi");
var p = QProduct.PRODUCT.as("p");

Query q = dsl.select(
      o.id().as("order_id"),
      o.orderNo().as("order_no"),
      m.name().as("merchant_name"),
      function("coalesce", m.displayName(), m.name()).as("merchant_display_name"),
      function("sum",
          function("sub",
              function("mul", oi.qty(), oi.unitPrice()),
              function("coalesce", oi.discount(), literal(0))
          )
      ).as("gross_amount")
    )
    .from(o)
    .leftJoin(m).on(m.id().eq(o.merchantId()))
    .leftJoin(oi).on(oi.orderId().eq(o.id()))
    .leftJoin(p).on(p.id().eq(oi.productId()))
    .where(w -> {
       if (!f.merchantIds().isEmpty()) w.and(o.merchantId().in(f.merchantIds()));
       if (f.keyword() != null) {
         w.and(function("lower", o.orderNo()).like("%" + f.keyword().toLowerCase() + "%"));
       }
    })
    .groupBy(o.id(), o.orderNo(), m.name())
    .orderBy(ob -> ob
        .allow(OrderSort.GROSS_DESC, col("gross_amount").desc())
        .allow(OrderSort.CREATED_DESC, o.createdAt().desc())
        .use(f.sort(), OrderSort.CREATED_DESC))
    .page(f.page(), f.pageSize());
```

### 8.3 DML DSL（insert/update/delete）

```java
InsertStmt insert = dsl.insertInto(o)
    .columns(o.col("id"), o.col("status"))
    .row(param("id"), "NEW")
    .build();

UpdateStmt update = dsl.update(o)
    .set(o.col("status"), param("status"))
    .where(o.col("id").eq(param("id")))
    .build();

// Lambda 字段引用（方法引用会缓存解析结果，避免重复反射）
var orders = dsl.table(Order.class);
UpdateStmt updateByLambda = dsl.update(orders)
    .set(Order::status, param("status"))
    .where(orders.col(Order::id).eq(param("id")))
    .build();

DeleteStmt delete = dsl.deleteFrom(o)
    .where(o.col("id").eq(param("id")))
    .build();
```

---

## 9. 函数使用（直接 SQL + Dialect Hook）

不再内置 `@fn` 或函数包装层。模板里直接写函数名；DSL 里用 `Expr.Func`
或 `Dsl.function(...)` 构造函数调用。

Dialect 提供 `renderFunction` 作为唯一扩展点：默认实现按 `name(arg1, arg2)`
渲染，需要方言适配时由用户自行覆盖。

```java
public interface Dialect {
  default RenderedSql renderFunction(String name, List<RenderedSql> args) { ... }
}
```

---

## 10. 执行层：JDBC Executor 与映射

> **现状**：已提供 `JdbcExecutor`、`Db/Query/Command`、批量/流式执行、
> 生成主键与乐观锁辅助，覆盖查询与写入的主路径。

### 10.1 执行 API

```java
public interface Db {
  <T> List<T> fetch(Query query, RowMapper<T> mapper);
  int execute(Command command);
  int executeOptimistic(Command command); // 影响行数 != 1 时抛 OptimisticLockException
  <T> T executeAndReturnGeneratedKey(Command command, String columnLabel, GeneratedKeyMapper<T> mapper);
  int[] executeBatch(BatchSql batchSql);
  <T> ResultStream<T> fetchStream(Query query, RowMapper<T> mapper, int fetchSize);
  <T> List<T> run(String sqlTextBlock, Bindings bindings, RowMapper<T> mapper); // 运行时路径
}
```

其中：

* `Query/Command` 都持有 AST 或已渲染的 `RenderedSql`
* `Bindings` 支持命名参数到值的映射（模板入口需要）

### 10.2 RowMapper（建议）

提供三种映射：

1. `RowMapper<T>` 手写映射（最可靠）；
2. `Record/DTO` 反射映射（开发效率高）；
3. APT 生成映射（高性能、强校验）。

### 10.3 写入与批量执行（现状）

已实现的核心能力：

1. **DML AST + Renderer**：`InsertStmt/UpdateStmt/DeleteStmt`，统一绑定模型与渲染规则。
2. **写入 DSL**：`insertInto(table).values(...)`、`update(table).set(...).where(...)`、`deleteFrom(table).where(...)`。
3. **批量执行 API**：`BatchSql + executeBatch(...)`，支持批量大小控制。
4. **读写优化**：`fetchStream` 支持大结果集读取，写入可按批次切分。
5. **增强语义**：`executeAndReturnGeneratedKey(...)` 与 `executeOptimistic(...)`。

后续增强方向：Dialect 专属批量通道、`upsert` 等。

### 10.4 可观测性 Hook（渲染/执行）

提供 `DbObserver` 监听渲染与执行阶段：

* `beforeRender/afterRender`：可记录 SQL 渲染耗时；
* `beforeExecute/afterExecute`：可记录执行耗时、影响行数/返回行数；
* `onRenderError/onExecuteError`：用于埋点错误与失败场景。

#### 10.4.1 SQL 打印插件（可选）

通过 `SqlLog` 作为 `DbObserver` 插件挂载到 `DefaultDb`：

```java
SqlLog log = SqlLog.builder()
    .mode(SqlLog.Mode.INLINE)     // INLINE: 内联参数；SEPARATE: SQL + binds
    .includeElapsed(true)         // 可选：记录耗时
    .includeRowCount(true)        // 可选：记录影响行数
    .build();

DefaultDb db = new DefaultDb(executor, renderer, dialect, metaRegistry, resolver, List.of(log));
```

默认只在执行阶段打印；可通过 `logOnRender(true)` 额外记录渲染阶段日志。

### 10.5 事务辅助（回调式）

提供 `TransactionManager`，在回调中复用同一连接：

```java
TransactionManager tx = new TransactionManager(dataSource, renderer, dialect, metaRegistry, nameResolver);
tx.inTransaction(db -> {
  db.execute(Command.of(renderedSql));
  return null;
});
```

### 10.6 批量执行 API

```java
RenderedSql template = new RenderedSql("UPDATE t SET v=?", List.of());
BatchSql batch = BatchSql.builder(template)
    .add(List.of(new Bind.Value(1, 0)))
    .add(List.of(new Bind.Value(2, 0)))
    .batchSize(500)
    .build();

int[] results = db.executeBatch(batch);
```

### 10.7 大结果集流式读取

```java
try (ResultStream<Row> stream = db.fetchStream(query, mapper, 500)) {
  while (stream.next()) {
    Row row = stream.row();
    // handle row
  }
}
```

### 10.8 Active Record 模式（可选）

提供两种风格，满足不同偏好：

1. **MyBatis-Plus 风格**：`Model<T>`，方法返回 boolean，实例直接调用。
2. **行数风格**：`ActiveRecord<T>`，方法返回影响行数，保留可观测性。

配置入口（两种风格共用）：

```java
ActiveRecord.configure(ActiveRecordConfig.builder()
    .db(db)
    .renderer(renderer)
    .metaRegistry(metaRegistry)
    .filterLogicalDelete(true)
    .build());
```

MyBatis-Plus 风格示例：

```java
@Table(name = "orders")
class OrderRecord extends Model<OrderRecord> {
  @Id(strategy = IdStrategy.AUTO)
  private Long id;

  @Column(name = "status")
  private String status;

  @LogicDelete(active = "0", deleted = "1")
  private Integer deleted;
}

OrderRecord order = new OrderRecord();
order.status = "NEW";
order.insert();

order.status = "PAID";
order.updateById();

OrderRecord loaded = order.selectById(order.id);
List<OrderRecord> all = order.selectAll();

order.deleteById(); // 若存在 @LogicDelete，自动走逻辑删除
```

DSL 风格链式查询/更新/删除示例：

```java
Model.of(OrderRecord.class)
    .select(OrderRecord::status)
    .where(OrderRecord::id).eq(1L)
    .one();

Model.of(OrderRecord.class)
    .where(OrderRecord::status).eq("NEW")
    .page(1, 10);

Model.of(OrderRecord.class)
    .set(OrderRecord::status, "PAID")
    .where(OrderRecord::id).eq(1L)
    .update();

Model.of(OrderRecord.class)
    .set(OrderRecord::status, "NEW")
    .save();

Model.of(OrderRecord.class)
    .where(OrderRecord::id).eq(1L)
    .remove();

Model.of(OrderRecord.class)
    .set(OrderRecord::id, 1L)
    .removeById();
```

提示：

* 统一使用 `Model.of(Entity.class)` 作为 ActiveQuery 入口，避免静态推断歧义。

JOIN 示例（查询自身列 + 关联列）：

```java
ActiveQuery<OrderRecord> query = Model.of(OrderRecord.class);
Table orders = query.table();
Table items = query.table(OrderItemRecord.class).as("oi");

List<OrderRecord> rows = query
    .select(orders.col(OrderRecord::id), orders.col(OrderRecord::status), items.col("sku"))
    .leftJoin(items).on(items.col("orderId").eq(orders.col(OrderRecord::id)))
    .where(orders.col(OrderRecord::id)).eq(1L)
    .objList();
```

行数风格示例：

```java
class OrderRecord extends ActiveRecord<OrderRecord> { ... }

OrderRecord order = new OrderRecord();
order.status = "NEW";
int inserted = order.insert();
```

关联查询（简单外键关系，非 JOIN）：

```java
List<OrderItemRecord> items = order.hasMany(OrderItemRecord.class, OrderItemRecord::orderId);
OrderItemRecord one = order.hasOne(OrderItemRecord.class, OrderItemRecord::orderId);
OrderRecord parent = item.belongsTo(OrderRecord.class, OrderItemRecord::orderId, OrderRecord::id);
```

回调方法（可覆盖）：

```java
@Override protected void beforeInsert() {}
@Override protected void afterInsert(int rows) {}
@Override protected void beforeUpdate() {}
@Override protected void afterUpdate(int rows) {}
@Override protected void beforeDelete(boolean logical) {}
@Override protected void afterDelete(int rows, boolean logical) {}
@Override protected void beforeSave() {}
@Override protected void afterSave(int rows) {}
```

说明：

* Active Record 默认使用 `RowMappers.auto` 做映射，实体需有无参构造器；
* 关联方法会触发额外查询，不会自动生成 JOIN；
* `filterLogicalDelete(true)` 会在 `find/list` 中自动追加 `notDeleted()`。
* `update()` / `remove()` 默认要求有 where 条件，避免误操作全表；如需全表更新/删除建议使用 DSL 直接构造语句。

---

## 11. 编译期检查与生成（APT）

### 11.1 `@SqlTemplate` 入口（强校验主路径）

```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface SqlTemplate {
  String value();
}
```

APT 对每个方法：

* 解析模板 AST；
* 校验绑定参数存在性与类型可用性；
* 校验 `@orderBy.allowed` 白名单；
* 生成实现类（或生成静态 `SqlText` 对象）：

```java
public final class OrderRepo_Impl implements OrderRepo {
  private final Db db;
  private final Dialect dialect;
  private final EntityMetaRegistry metaRegistry;
  private final EntityNameResolver entityNameResolver;

  public OrderRepo_Impl(
      Db db,
      Dialect dialect,
      EntityMetaRegistry metaRegistry,
      EntityNameResolver entityNameResolver
  ) { ... }

  @Override
  public List<OrderRow> search(
      Filter filter,
      String kw,
      OrderSort sort,
      int page,
      int pageSize,
      RowMapper<OrderRow> mapper
  ) throws SQLException {
    // 生成绑定并渲染模板，交给 Db 执行
    Bindings bindings = ...;
    RenderedSql rs = ...;
    return db.fetch(Query.of(rs), mapper);
  }
}
```

约束约定：

* `@SqlTemplate` 方法必须声明 `throws SQLException`（执行层需要）；
* `List<T>` 返回值的方法必须包含 `RowMapper<T>` 参数；
* 模板绑定名来自方法参数名（忽略 `RowMapper`）。

### 11.2 `.run("""...""")` 的编译期增强方案

* 方案 S1：`@SqlConst static final String Q = """..."""`（APT 可读取字段常量并校验）
* 方案 S2：编译器插件（javac plugin / Error Prone）扫描方法调用的字面量字符串并校验（实现成本高，作为高级特性）

---

## 12. 演示代码：4 表复杂动态查询（模板 + DSL 两种）

> 以下为“使用体验展示”，体现最终效果形态。
> 实现细节在前述章节已给出原理路径。

### 12.1 模板形式（Text Block + if/for）

```java
public interface OrderRepo {

  @SqlTemplate("""
    SELECT
      o.id AS order_id,
      o.order_no AS order_no,
      m.name AS merchant_name,
      coalesce(m.display_name, m.name) AS merchant_display_name,
      o.status,
      o.created_at,
      o.paid_at,
      json_text(o.ext_json, '$.channel') AS channel,
      date_trunc('day', o.created_at) AS created_day,
      COUNT(DISTINCT oi.id) AS item_count,
      sum((oi.qty * oi.unit_price) - coalesce(oi.discount, 0)) AS gross_amount

    FROM @table(Order) o
    JOIN @table(Merchant) m   ON m.id = o.merchant_id
    JOIN @table(OrderItem) oi ON oi.order_id = o.id
    JOIN @table(Product) p    ON p.id = oi.product_id

    @where {
      @if(!filter.includeDeleted) { o.deleted_at IS NULL }

      @if(!filter.merchantIds.isEmpty()) {
        AND o.merchant_id IN @in(:filter.merchantIds)
      }

      @if(!filter.statuses.isEmpty()) {
        AND o.status IN @in(:filter.statuses)
      }

      @if(filter.createdFrom != null) { AND o.created_at >= :filter.createdFrom }
      @if(filter.createdTo   != null) { AND o.created_at <  :filter.createdTo }

      @if(filter.onlyPaid) { AND o.paid_at IS NOT NULL }

      @if(filter.keyword != null && filter.keyword != '') {
        AND (
          lower(o.order_no) LIKE :kw
          OR lower(p.name)  LIKE :kw
          OR lower(m.name)  LIKE :kw
        )
      }

      @if(!filter.categoryIds.isEmpty()) {
        AND p.category_id IN @in(:filter.categoryIds)
      }

      @if(filter.brand != null && filter.brand != '') {
        AND p.brand = :filter.brand
      }

      @if(!filter.channels.isEmpty()) {
        AND json_text(o.ext_json, '$.channel') IN @in(:filter.channels)
      }

      @if(!filter.anyTags.isEmpty()) {
        AND (
          @for(tag : filter.anyTags) { @or { json_contains(p.tags_json, :tag) } }
        )
      }
    }

    GROUP BY o.id, o.order_no, m.name, m.display_name, o.status, o.created_at, o.paid_at, o.ext_json

    @having {
      @if(filter.minGrossAmount != null) { gross_amount >= :filter.minGrossAmount }
      @if(filter.maxGrossAmount != null) { AND gross_amount <= :filter.maxGrossAmount }
    }

    @orderBy(:sort, allowed = {
      CREATED_DESC : o.created_at DESC,
      GROSS_DESC   : gross_amount DESC,
      ITEMS_DESC   : item_count DESC
    }, default = CREATED_DESC)

    @page(:page, :pageSize)
  """)
  List<OrderRow> search(@Bind("filter") Filter filter,
                        @Bind("kw") String kw,
                        @Bind("sort") OrderSort sort,
                        @Bind("page") int page,
                        @Bind("pageSize") int pageSize);
}
```

### 12.2 DSL 形式（select/from/leftJoin）

```java
import static io.lighting.lumen.dsl.Dsl.function;
import static io.lighting.lumen.dsl.Dsl.literal;

var o  = QOrder.ORDER.as("o");
var m  = QMerchant.MERCHANT.as("m");
var oi = QOrderItem.ORDER_ITEM.as("oi");
var p  = QProduct.PRODUCT.as("p");

Query q = dsl.select(
      o.id().as("order_id"),
      o.orderNo().as("order_no"),
      m.name().as("merchant_name"),
      function("coalesce", m.displayName(), m.name()).as("merchant_display_name"),
      o.status(),
      o.createdAt(),
      o.paidAt(),
      function("json_text", o.extJson(), literal("$.channel")).as("channel"),
      function("date_trunc", literal("day"), o.createdAt()).as("created_day"),
      function("count_distinct", oi.id()).as("item_count"),
      function(
        "sum",
        function(
          "sub",
          function("mul", oi.qty(), oi.unitPrice()),
          function("coalesce", oi.discount(), literal(0))
        )
      ).as("gross_amount")
    )
    .from(o)
    .join(m).on(m.id().eq(o.merchantId()))
    .join(oi).on(oi.orderId().eq(o.id()))
    .join(p).on(p.id().eq(oi.productId()))
    .where(w -> {
      if (!f.includeDeleted()) w.and(o.deletedAt().isNull());
      if (!f.merchantIds().isEmpty()) w.and(o.merchantId().in(f.merchantIds()));
      if (!f.statuses().isEmpty()) w.and(o.status().in(f.statuses()));
      if (f.createdFrom() != null) w.and(o.createdAt().ge(f.createdFrom()));
      if (f.createdTo() != null) w.and(o.createdAt().lt(f.createdTo()));
      if (f.onlyPaid()) w.and(o.paidAt().isNotNull());

      if (f.keyword() != null && !f.keyword().isBlank()) {
        String kw = "%" + f.keyword().toLowerCase() + "%";
        w.and(orGroup(g -> g
          .or(function("lower", o.orderNo()).like(kw))
          .or(function("lower", p.name()).like(kw))
          .or(function("lower", m.name()).like(kw))
        ));
      }

      if (!f.channels().isEmpty()) {
        w.and(function("json_text", o.extJson(), literal("$.channel")).in(f.channels()));
      }
    })
    .groupBy(o.id(), o.orderNo(), m.name(), m.displayName(), o.status(), o.createdAt(), o.paidAt(), o.extJson())
    .having(h -> {
      if (f.minGrossAmount() != null) h.and(col("gross_amount").ge(f.minGrossAmount()));
    })
    .orderBy(ob -> ob
      .allow(OrderSort.CREATED_DESC, o.createdAt().desc())
      .allow(OrderSort.GROSS_DESC, col("gross_amount").desc())
      .use(f.sort(), OrderSort.CREATED_DESC))
    .page(f.page(), f.pageSize());

List<OrderRow> rows = db.fetch(q, OrderRowMapper.INSTANCE);
```

---

## 13. `.run("""...""")` 与“编译器检查”的统一方案

你要求：

* 模板注解支持编译器检查；
* 同时能通过 `.run("""...""")` 直接运行。

建议提供三条路径并保持一致语义：

1. **强校验主路径**：`@SqlTemplate`（APT 读取模板，生成执行实现）
2. **可复用且可校验的 SQL 常量**：

```java
@SqlConst
static final String Q = """
  SELECT ... WHERE id = :id
""";
db.run(Q, Bindings.of("id", id), mapper);
```

3. **临时运行**（运行时解析）：

```java
db.run("""
  SELECT ... WHERE id = :id
""", Bindings.of("id", id), mapper);
```

如需让第 3 条也“编译期检查”，则引入编译器插件（高级特性）。

---

## 14. 安全性与正确性（形式化约束）

### 14.1 两类输入的强隔离

* **Value Bind**：`:param` → `?` + bind（默认）
* **Identifier**：表/列/排序片段 → 仅允许元模型/白名单输出

任何将 `:param` 用于标识符位置的行为应在编译期/运行期拒绝。

### 14.2 动态 SQL 的结构化拼装

`@where/@having/@or/@in` 不是语法糖，而是用于防止：

* `AND` 前缀错误、空块导致语法不完整；
* `IN ()` 空集合造成语法错误；
* 动态排序注入。

---

## 15. 性能与工程化

* **模板 APT 路径**：零运行时解析开销；渲染只发生在参数绑定层面。
* **反射元模型**：首次加载成本可控，之后缓存命中；APT 可进一步提升性能并增强校验。
* **Renderer**：建议无正则、无字符串拼接热点；用 `StringBuilder` 或分段写入器。
* **可观测性**：Renderer/Executor 暴露 hook：

  * beforeRender / afterRender
  * beforeExecute / afterExecute（含耗时与异常）
  * 输出结构化事件（SQL、binds、数据库信息、调用点）

---

## 16. 风险与边界

* 如果未来需求扩展到“复杂方言函数全覆盖”，框架会被迫演化为方言翻译器；本文明确不走此路线。
* 模板语言设计若过度扩张（引入过多语法），会变成“再造一门语言”；建议控制指令集合并引导业务在 DSL/Java 控制流中组合复杂逻辑。
* 编译器插件路线工程成本高，应作为可选高级增强，不作为 MVP 前置依赖。

---

## 17. MVP 交付路线（建议）

1. AST + Renderer + JDBC Executor（含 binds）
2. EntityMeta 反射缓存 + `@table/@col` 宏
3. DSL：select/from/join/where/order/page
4. DML：insert/update/delete + 批量执行策略
5. 模板：@if/@for/@where/@in/@orderBy/@page
6. APT：`@SqlTemplate` 编译期校验与实现生成
7. 可观测性与安全门禁（排序白名单、危险 RawSql 默认禁用）

---

## 18. Docker Compose 集成测试（MySQL/Postgres/Oracle）

提供 `docker-compose.yml` 与初始化脚本，可运行真实数据库集成测试：

```bash
docker compose up -d
```

连接信息在测试类中写死，默认值如下：

* MySQL: `jdbc:mysql://localhost:3307/lumen` / `root` / `root`
* Postgres: `jdbc:postgresql://localhost:5432/lumen` / `lumen` / `lumen`
* Oracle: `jdbc:oracle:thin:@localhost:1521/FREEPDB1` / `lumen` / `lumen`（主键列名 `ID`）

如需变更，请直接修改 `src/test/java/io/lighting/lumen/integration/DockerComposeIntegrationTest.java`。

---

## 附录 A：关键 API 草案（汇总）

```java
// 入口 1：模板（强校验）
@Retention(SOURCE) @Target(METHOD)
@interface SqlTemplate { String value(); }

@Retention(SOURCE) @Target({FIELD, LOCAL_VARIABLE})
@interface SqlConst {}

// 入口 2：DSL
interface Dsl {
  SelectBuilder select(Expr... items);
}

// 统一执行
interface Db {
  <T> List<T> fetch(Query query, RowMapper<T> mapper);
  <T> List<T> run(String sqlText, Bindings bindings, RowMapper<T> mapper);
  int execute(Command command);
}
```

## 附录：APT 与运行时代理

如果你关心 `@SqlTemplate` 在编译期（APT）与运行时代理的关系、使用方式与性能取舍，请看：

- `docs/APT_GUIDE.md`

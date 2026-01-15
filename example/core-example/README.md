# Core Examples

This module contains small, focused examples for the lumen-core API surface. The code
snippets below are intended to be copy-paste friendly and map to the production APIs.

## Dependencies

```xml
<dependency>
  <groupId>io.lighting</groupId>
  <artifactId>lumen-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## Bootstrap Db

```java
DataSource dataSource = /* your pool */;
Dialect dialect = DialectResolver.resolve(dataSource);
EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
EntityNameResolver nameResolver = EntityNameResolvers.auto();
SqlRenderer renderer = new SqlRenderer(dialect);
JdbcExecutor executor = new JdbcExecutor(dataSource);
Db db = new DefaultDb(executor, renderer, dialect, metaRegistry, nameResolver);
```

## Lumen Bootstrap

```java
Lumen lumen = Lumen.builder()
    .dataSource(dataSource)
    .build();

Db db = lumen.db();
Dsl dsl = lumen.dsl();
```

可选配置（按需打开）：

```java
Lumen lumen = Lumen.builder()
    .dataSource(dataSource)
    // 方言：默认按 DataSource 自动识别，也可显式覆盖
    .dialect(new LimitOffsetDialect("mysql", "`"))
    // 元数据：@Table/@Column/@Id 的映射来源
    .metaRegistry(new ReflectionEntityMetaRegistry())
    // 模板解析：默认扫描 @Table 实体，可按需补充短名映射
    .entityNameMappings(Map.of(
        "OrderRecord", OrderRecord.class,
        "OrderItemRecord", OrderItemRecord.class,
        "OrderModel", OrderModel.class
    ))
    .build();
```

## DSL Select + Join + Paging

```java
Dsl dsl = new Dsl(metaRegistry);
Table orders = dsl.table(OrderRecord.class).as("o");
Table items = dsl.table(OrderItemRecord.class).as("i");

SelectStmt stmt = dsl.select(
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

RenderedSql rendered = renderer.render(stmt, Bindings.of("status", "NEW"));
```

## DSL DML (Insert / Update / Delete)

```java
InsertStmt insert = dsl.insertInto(orders)
    .columns(orders.col(OrderRecord::getId), orders.col(OrderRecord::getStatus))
    .row(Dsl.param("id"), Dsl.param("status"))
    .build();

UpdateStmt update = dsl.update(orders)
    .set(OrderRecord::getStatus, Dsl.param("status"))
    .where(orders.col(OrderRecord::getId).eq(Dsl.param("id")))
    .build();

DeleteStmt delete = dsl.deleteFrom(orders)
    .where(orders.col(OrderRecord::getId).eq(Dsl.param("id")))
    .build();
```

## Runnable H2 Example (Db.run + execute)

```java
Lumen lumen = Lumen.builder()
    .dataSource(dataSource)
    .build();

Db db = lumen.db();
db.execute(Command.of(new RenderedSql(
    "CREATE TABLE orders (id BIGINT PRIMARY KEY, order_no VARCHAR(64), status VARCHAR(32), total DECIMAL(10,2))",
    List.of()
)));

RenderedSql insert = new RenderedSql(
    "INSERT INTO orders(id, order_no, status, total) VALUES (?, ?, ?, ?)",
    List.of(new Bind.Value(1L, 0), new Bind.Value("NO-1", 0), new Bind.Value("NEW", 0), new Bind.Value(10, 0))
);
db.execute(Command.of(insert));

List<OrderRow> rows = db.run(
    "SELECT id, order_no, status FROM orders WHERE status = :status",
    Bindings.of("status", "NEW"),
    rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
);
```

## H2 SqlTemplate Example

```java
@SqlConst
static final String FIND_SQL = """
    SELECT o.id, o.order_no, o.status
    FROM @table(io.lighting.lumen.example.OrderRecord) o
    @where {
      @if(status != null) { o.status = :status }
      @if(ids != null && !ids.isEmpty()) { AND o.id IN @in(:ids) }
    }
    """;

List<OrderRow> rows = db.run(
    FIND_SQL,
    Bindings.of("status", "NEW", "ids", List.of(1L, 2L)),
    rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
);
```

## H2 Lambda DSL Example

```java
List<OrderRow> rows = db.dsl()
    .select(OrderRow.class, OrderRecord::getId, OrderRecord::getOrderNo, OrderRecord::getStatus)
    .from(OrderRecord.class)
    .where()
    .equals(OrderRecord::getStatus, "NEW")
    .toList();
```

## Template SQL

```java
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

Bindings bindings = Bindings.of(
    "status", "NEW",
    "ids", List.of(10L, 20L),
    "sort", "STATUS_ASC",
    "page", 1,
    "pageSize", 50
);

List<OrderRow> rows = db.run(
    sql,
    bindings,
    rs -> new OrderRow(rs.getLong("id"), null, rs.getString("status"))
);
```

说明：默认会扫描 `@Table` 实体用于短名映射；若存在重名，可用全限定名或通过
`entityNameMappings`/`addEntityNameMapping` 做补充。

## Db.run (Template at Runtime)

```java
List<OrderRow> rows = db.run(
    sql,
    bindings,
    rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status"))
);
```

## ActiveRecord Setup

```java
ActiveRecord.configure(ActiveRecordConfig.builder()
    .db(db)
    .renderer(renderer)
    .metaRegistry(metaRegistry)
    .filterLogicalDelete(true)
    .build());
```

## ActiveRecord Usage

```java
OrderRecord order = new OrderRecord();
order.setStatus("NEW");
order.insert();

OrderRecord loaded = order.selectById(order.getId());
loaded.setStatus("PAID");
loaded.update();
```

## Model / ActiveQuery Usage

```java
List<OrderModel> rows = Model.of(OrderModel.class)
    .select(OrderModel::getId, OrderModel::getStatus)
    .where(OrderModel::getStatus).eq("NEW")
    .page(1, 20);
```

## Transaction Manager

```java
TransactionManager tx = new TransactionManager(
    dataSource,
    renderer,
    dialect,
    metaRegistry,
    nameResolver
);

Integer rows = tx.inTransaction(dbInTx -> dbInTx.execute(Command.of(rendered)));
```

## Batch Execute

```java
RenderedSql template = new RenderedSql(
    "UPDATE \"orders\" SET \"status\" = ? WHERE \"id\" = ?",
    List.of()
);

BatchSql batch = BatchSql.builder(template)
    .add(List.of(new Bind.Value("PAID", 0), new Bind.Value(100L, 0)))
    .add(List.of(new Bind.Value("CANCELLED", 0), new Bind.Value(101L, 0)))
    .batchSize(500)
    .build();

int[] results = db.executeBatch(batch);
```

## Stream Fetch

```java
try (ResultStream<OrderRow> stream = db.fetchStream(
    Query.of(rendered),
    rs -> new OrderRow(rs.getLong("id"), rs.getString("order_no"), rs.getString("status")),
    500
)) {
    while (stream.next()) {
        OrderRow row = stream.row();
        // handle row
    }
}
```

## Generated Keys

```java
Long id = db.executeAndReturnGeneratedKey(
    Command.of(rendered),
    "id",
    keys -> keys.getLong(1)
);
```

## SQL Logging

```java
SqlLog log = SqlLog.builder()
    .mode(SqlLog.Mode.INLINE)
    .includeElapsed(true)
    .includeRowCount(true)
    .build();

Db dbWithLog = new DefaultDb(executor, renderer, dialect, metaRegistry, nameResolver, List.of(log));
```

## Property Name Helpers

```java
String name = PropertyNames.name(OrderRecord::getStatus); // "status"
Class<?> owner = PropertyNames.owner(OrderRecord::getStatus); // OrderRecord.class
```

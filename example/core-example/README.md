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
Dialect dialect = new LimitOffsetDialect("\"");
EntityMetaRegistry metaRegistry = new ReflectionEntityMetaRegistry();
EntityNameResolver nameResolver = EntityNameResolvers.from(Map.of(
    "OrderRecord", OrderRecord.class,
    "OrderItemRecord", OrderItemRecord.class
));
SqlRenderer renderer = new SqlRenderer(dialect);
JdbcExecutor executor = new JdbcExecutor(dataSource);
Db db = new DefaultDb(executor, renderer, dialect, metaRegistry, nameResolver);
```

## Lumen Bootstrap

```java
Lumen lumen = Lumen.builder()
    .dataSource(dataSource)
    .dialect(dialect)
    .metaRegistry(metaRegistry)
    .entityNameResolver(EntityNameResolvers.from(Map.of(
        "OrderRecord", OrderRecord.class,
        "OrderItemRecord", OrderItemRecord.class,
        "OrderModel", OrderModel.class
    )))
    .build();

OrderDao dao = lumen.dao(OrderDao.class);
Dsl dsl = lumen.dsl();
Db db = lumen.db();
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

SqlTemplate template = SqlTemplate.parse(sql);
TemplateContext context = new TemplateContext(bindings.asMap(), dialect, metaRegistry, nameResolver);
RenderedSql rendered = template.render(context);
```

## DAO Interface (SqlTemplate)

```java
public interface OrderDao {
  @SqlTemplate("""
    SELECT o.id, o.order_no, o.status
    FROM @table(OrderRecord) o
    WHERE o.id = :id
  """)
  List<OrderRow> findById(Long id, RowMapper<OrderRow> mapper) throws SQLException;

  @SqlTemplate("""
    SELECT o.id, o.order_no, o.status
    FROM @table(OrderRecord) o
    @where {
      @if(filter != null && filter.status != null) { o.status = :filter.status }
      @if(filter != null && filter.ids != null && !filter.ids.isEmpty()) {
        AND o.id IN @in(:filter.ids)
      }
    }
    @orderBy(:sort, allowed = { CREATED_DESC : o.id DESC, STATUS_ASC : o.status ASC }, default = CREATED_DESC)
    @page(:page, :pageSize)
  """)
  List<OrderRow> search(
      OrderFilter filter,
      String sort,
      int page,
      int pageSize,
      RowMapper<OrderRow> mapper
  ) throws SQLException;

  @SqlTemplate("""
    UPDATE @table(OrderRecord)
    SET status = :status
    WHERE id = :id
  """)
  int updateStatus(Long id, String status) throws SQLException;
}
```

```java
OrderDao dao = new OrderDao_Impl(db, dialect, metaRegistry, nameResolver);
OrderFilter filter = new OrderFilter(List.of(1L, 2L), "NEW");
List<OrderRow> rows = dao.search(filter, "CREATED_DESC", 1, 20, mapper);
```

Note: `@table(OrderRecord)` resolves through `EntityNameResolver`, so map short names like
`OrderRecord` to the entity class when building the DAO instance.

## Db.run (Template at Runtime)

```java
List<OrderRow> rows = db.run(sql, bindings, OrderRowMapper.INSTANCE);
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
try (ResultStream<OrderRow> stream = db.fetchStream(Query.of(rendered), OrderRowMapper.INSTANCE, 500)) {
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

## APT Entry Points

```java
@SqlTemplate("""
  SELECT o.id, o.status
  FROM @table(OrderRecord) o
  WHERE o.id = :id
""")
List<OrderRow> findById(Long id, RowMapper<OrderRow> mapper) throws SQLException;
```

```java
@SqlConst
static final String FIND_ALL = """
  SELECT o.id, o.status
  FROM @table(OrderRecord) o
""";
```

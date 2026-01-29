# Active Record Guide

Use the Active Record pattern for simple CRUD operations.

## Define Active Record Entity

```java
@Table(name = "orders")
class OrderRecord extends Model<OrderRecord> {
    @Id(strategy = IdStrategy.AUTO)
    private Long id;

    @Column(name = "status")
    private String status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;
}
```

## Quick CRUD

```java
// Create
OrderRecord order = new OrderRecord();
order.setStatus("NEW");
order.setTotalAmount(new BigDecimal("99.99"));
order.insert();

// Read
OrderRecord loaded = order.selectById(1L);
List<OrderRecord> all = order.selectAll();

// Update
order.setStatus("PAID");
order.updateById();

// Delete
order.deleteById();
```

## Query with Lambda

```java
OrderRecord order = Model.of(OrderRecord.class)
    .select(OrderRecord::getStatus, OrderRecord::getTotalAmount)
    .where(OrderRecord::getId).eq(1L)
    .one();

List<OrderRecord> pending = Model.of(OrderRecord.class)
    .where(OrderRecord::getStatus).eq("NEW")
    .list();
```

## Pagination

```java
PageResult<OrderRecord> page = Model.of(OrderRecord.class)
    .where(OrderRecord::getStatus).eq("NEW")
    .page(PageRequest.of(1, 20));
```

## Update Chain

```java
int updated = Model.of(OrderRecord.class)
    .set(OrderRecord::getStatus, "PAID")
    .where(OrderRecord::getStatus).eq("NEW")
    .update();
```

## Delete Chain

```java
int deleted = Model.of(OrderRecord.class)
    .where(OrderRecord::getStatus).eq("CANCELLED")
    .remove();
```

## Joins

```java
ActiveQuery<OrderRecord> query = Model.of(OrderRecord.class);
Table orders = query.table();
Table items = query.table(OrderItemRecord.class).as("oi");

List<OrderRecord> rows = query
    .select(orders.col(OrderRecord::getId), orders.col(OrderRecord::getStatus), items.col("sku"))
    .leftJoin(items).on(items.col("orderId").eq(orders.col(OrderRecord::getId)))
    .where(orders.col(OrderRecord::getId)).eq(1L)
    .objList();
```

## Associations

```java
// hasMany
List<OrderItemRecord> items = order.hasMany(OrderItemRecord.class, OrderItemRecord::getOrderId);

// hasOne  
OrderItemRecord item = order.hasOne(OrderItemRecord.class, OrderItemRecord::getOrderId);

// belongsTo
OrderRecord parent = item.belongsTo(OrderRecord.class, OrderItemRecord::getOrderId, OrderRecord::getId);
```

## Lifecycle Hooks

```java
class OrderRecord extends Model<OrderRecord> {
    @Override
    protected void beforeInsert() {
        System.out.println("Before insert: " + getId());
    }

    @Override
    protected void afterInsert(int rows) {
        System.out.println("Inserted " + rows + " row(s)");
    }

    @Override
    protected void beforeUpdate() {}
    @Override
    protected void afterUpdate(int rows) {}
    @Override
    protected void beforeDelete(boolean logical) {}
    @Override
    protected void afterDelete(int rows, boolean logical) {}
}
```

## Configuration

```java
ActiveRecord.configure(ActiveRecordConfig.builder()
    .db(db)
    .renderer(renderer)
    .metaRegistry(metaRegistry)
    .filterLogicalDelete(true)  // Auto-filter deleted records
    .build());
```

## Benefits

- Simple and intuitive API
- Less boilerplate code
- Natural object-oriented style
- Good for CRUD-heavy applications

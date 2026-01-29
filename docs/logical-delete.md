# Logical Delete Guide

Implement soft delete with `@LogicDelete`.

## Define Logical Delete

```java
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "status")
    private String status;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "deleted")
    private Integer deleted;  // 0 = active, 1 = deleted
}
```

## Supported Types

```java
@LogicDelete(active = "0", deleted = "1")
private Integer deleted;     // Integer

@LogicDelete(active = "false", deleted = "true")
private Boolean deleted;     // Boolean

@LogicDelete(active = "N", deleted = "Y")
private String deleted;      // String
```

## DSL Logical Delete

```java
var t = dsl.table(Order.class).as("o");

// Logical delete update
UpdateStmt stmt = dsl.logicalDeleteFrom(t)
    .where(t.col(Order::getId).eq(1L))
    .build();

db.execute(Command.of(lumen.renderer().render(stmt, Bindings.empty())));
// Generates: UPDATE orders SET deleted = 1 WHERE id = ?
```

## Filter Active Records

```java
var t = dsl.table(Order.class).as("o");

// Automatically add "deleted = 0" to where clause
SelectStmt stmt = dsl.select(Dsl.item(t.col(Order::getId).expr()))
    .from(t)
    .where(w -> w.and(t.notDeleted()))  // Adds: AND deleted = 0
    .build();
```

## Active Record Pattern

```java
@Table(name = "orders")
class OrderRecord extends Model<OrderRecord> {
    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "deleted")
    private Integer deleted;
}

// Delete (logical)
OrderRecord order = new OrderRecord();
order.setId(1L);
order.deleteById();  // Updates deleted = 1

// Find (automatically filters deleted)
List<OrderRecord> active = order.where(order.col(OrderRecord::getId).eq(1L)).list();
// Only returns records where deleted = 0
```

## Configuration

### Filter by Default

```java
ActiveRecord.configure(ActiveRecordConfig.builder()
    .db(db)
    .filterLogicalDelete(true)  // Automatically filter deleted records
    .build());
```

### Custom Active Value

```java
@LogicDelete(active = "ACTIVE", deleted = "DELETED")
private String status;  // Uses custom values
```

## How It Works

1. **Definition**: Mark a field with `@LogicDelete`
2. **Logical Delete**: Updates the field to "deleted" value instead of removing the row
3. **Automatic Filtering**: Use `notDeleted()` to filter active records
4. **Query Support**: All queries work normally with logical delete semantics

## Comparison

| Operation | Physical Delete | Logical Delete |
|-----------|----------------|----------------|
| Remove data | DELETE FROM orders | UPDATE orders SET deleted = 1 |
| Find active | SELECT * FROM orders | SELECT * FROM orders WHERE deleted = 0 |
| Undelete | N/A | UPDATE orders SET deleted = 0 |

## Best Practices

- Use `Integer` or `Boolean` for simplest implementation
- Document the active/deleted values clearly
- Use `notDeleted()` consistently in queries
- Consider indexing the deleted column for query performance

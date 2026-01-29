# Transaction Guide

Manage database transactions with LumenORM.

## Basic Transaction

```java
TransactionManager tx = new TransactionManager(dataSource, renderer, dialect, metaRegistry);

tx.inTransaction(db -> {
    // All operations within this block share the same connection
    db.execute(Command.of(update1));
    db.execute(Command.of(update2));
    db.execute(Command.of(update3));
    return null;
});
```

## Return Values

```java
tx.inTransaction(db -> {
    int updated1 = db.execute(Command.of(update1));
    int updated2 = db.execute(Command.of(update2));
    return updated1 + updated2;  // Return value to caller
});
```

## Nested Transactions

```java
tx.inTransaction(db -> {
    // Outer transaction
    
    tx.inTransaction(innerDb -> {
        // Inner transaction (nested save point)
        innerDb.execute(Command.of(update1));
        return null;
    });
    
    db.execute(Command.of(update2));
    return null;
});
```

## Rollback

```java
tx.inTransaction(db -> {
    try {
        db.execute(Command.of(update1));
        
        if (somethingWentWrong) {
            throw new RuntimeException("Rollback!");  // Triggers rollback
        }
        
        db.execute(Command.of(update2));
        return null;
    } catch (RuntimeException e) {
        // Automatic rollback on exception
        return null;
    }
});
```

## Spring Integration

With Spring Boot starter, transactions are automatic:

```java
@Service
public class OrderService {

    @Autowired
    private Lumen lumen;

    @Transactional
    public void createOrder(Order order) {
        Db db = lumen.db();
        db.execute(Command.of(insertOrder));
        db.execute(Command.of(updateInventory));
    }
}
```

## Best Practices

- Keep transactions short to reduce lock contention
- Only include operations that must be atomic
- Handle exceptions appropriately
- Use nested transactions for complex operations
- Avoid long-running transactions

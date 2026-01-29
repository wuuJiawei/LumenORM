# Entity Definition Guide

Define your database tables as Java classes.

## @Table Annotation

```java
@Table(name = "pets")
public class Pet {
    // ...
}
```

## @Column Annotation

```java
@Table(name = "pets")
public class Pet {

    @Column(name = "id")
    private Long id;

    @Column(name = "pet_name")
    private String name;  // Maps 'name' field to 'pet_name' column

    // No annotation = field name as column name
    private String species;  // Column: 'species'
}
```

## @Id Annotation

```java
@Table(name = "pets")
public class Pet {

    @Id
    @Column(name = "id")
    private Long id;
}
```

### ID Strategies

```java
public enum IdStrategy {
    AUTO,      // Database auto-increment
    UUID,      // Generate UUID
    SNOWFLAKE  // Snowflake ID generator
}

@Table(name = "orders")
public class Order {

    @Id(strategy = IdStrategy.UUID)
    @Column(name = "id")
    private String id;

    @Id(strategy = IdStrategy.AUTO)  // Default
    @Column(name = "id")
    private Long id;
}
```

## @LogicDelete Annotation

```java
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id")
    private Long id;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "deleted")
    private Integer deleted;
}
```

See [Logical Delete](logical-delete.md) for details.

## Complete Example

```java
@Table(name = "orders")
public class Order {

    @Id(strategy = IdStrategy.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "status")
    private String status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @LogicDelete(active = "0", deleted = "1")
    @Column(name = "deleted")
    private Integer deleted;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Getters and setters
}
```

## Auto-Column Mapping

Fields without `@Column` annotation use the field name as column name:

```java
@Table(name = "pets")
public class Pet {
    private Long id;      // Column: 'id'
    private String name;  // Column: 'name'
    private Integer age;  // Column: 'age'
}
```

## Record Support

LumenORM supports Java records:

```java
@Table(name = "pets")
public record Pet(
    @Id @Column(name = "id") Long id,
    @Column(name = "name") String name,
    @Column(name = "species") String species
) {}
```

## Entity Registry

Entities are registered automatically via `EntityMetaRegistry`:

```java
EntityMetaRegistry registry = new ReflectionEntityMetaRegistry();
EntityMeta meta = registry.metaOf(Pet.class);

// meta.table() returns "pets"
// meta.fieldToColumn() returns {"id" -> "id", "name" -> "name", ...}
```

## Supported Field Types

- Primitive types and wrappers
- `String`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `LocalTime`
- `byte[]` for BLOBs
- Custom types with `TypeAdapter`

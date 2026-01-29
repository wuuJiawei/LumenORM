# Batch Operations Guide

Efficiently insert and update multiple records.

## Batch Insert

```java
RenderedSql template = new RenderedSql(
    "INSERT INTO pets (name, species, price) VALUES (?, ?, ?)",
    List.of()
);

BatchSql batch = BatchSql.builder(template)
    .add(List.of("Fluffy", "Cat", new BigDecimal("599.99")))
    .add(List.of("Buddy", "Dog", new BigDecimal("899.99")))
    .add(List.of("Whiskers", "Cat", new BigDecimal("449.99")))
    .batchSize(100)  // Execute in batches of 100
    .build();

int[] results = db.executeBatch(batch);
// results = [1, 1, 1] for 3 successful inserts
```

## Batch Update

```java
RenderedSql template = new RenderedSql(
    "UPDATE pets SET price = ? WHERE id = ?",
    List.of()
);

BatchSql batch = BatchSql.builder(template)
    .add(List.of(new BigDecimal("699.99"), 1L))
    .add(List.of(new BigDecimal("799.99"), 2L))
    .add(List.of(new BigDecimal("599.99"), 3L))
    .batchSize(500)
    .build();

int[] results = db.executeBatch(batch);
```

## Dynamic Batch Size

```java
int batchSize = 100;
for (List<Pet> batch : partition(pets, batchSize)) {
    BatchSql batchSql = buildBatchInsert(batch);
    db.executeBatch(batchSql);
}
```

## Performance Tips

- Use appropriate batch size (100-500 typically optimal)
- Reuse prepared statements
- Consider transaction boundaries
- Monitor database-specific batch limits

## Example: Bulk Import

```java
public void importPets(List<Pet> pets) throws SQLException {
    if (pets.isEmpty()) return;
    
    RenderedSql template = new RenderedSql(
        "INSERT INTO pets (name, species, breed, age, price) VALUES (?, ?, ?, ?, ?)",
        List.of()
    );
    
    BatchSql.Builder builder = BatchSql.builder(template);
    
    for (Pet pet : pets) {
        builder.add(List.of(
            pet.getName(),
            pet.getSpecies(),
            pet.getBreed(),
            pet.getAge(),
            pet.getPrice()
        ));
    }
    
    db.executeBatch(builder.batchSize(100).build());
}
```

##JDBC Batch vs Individual Statements

| Approach | Pros | Cons |
|----------|------|------|
| Batch | Fewer round-trips, better performance | Larger transactions |
| Individual | Smaller transactions, easier recovery | More round-trips |

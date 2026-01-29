# Fluent DSL Guide

Build type-safe queries with LumenORM's fluent DSL.

## Basic Select

```java
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(
        Dsl.item(t.col(Pet::getId).expr()),
        Dsl.item(t.col(Pet::getName).expr()),
        Dsl.item(t.col(Pet::getPrice).expr())
    )
    .from(t)
    .build();

List<Pet> pets = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
```

## Where Conditions

```java
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
    .from(t)
    .where(w -> w
        .and(t.col(Pet::getAvailable).eq(true))
        .and(t.col(Pet::getSpecies).like("%cat%"))
    )
    .build();
```

## OR Conditions

```java
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
    .from(t)
    .where(w -> w.orGroup(group -> group
        .or(t.col(Pet::getSpecies).eq("Cat"))
        .or(t.col(Pet::getSpecies).eq("Dog"))
    ))
    .build();
```

## IN Clause

```java
var t = dsl.table(Pet.class).as("p");
List<String> species = List.of("Cat", "Dog", "Bird");

SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
    .from(t)
    .where(w -> w.and(t.col(Pet::getSpecies).in(species)))
    .build();
```

## Order By

```java
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
    .from(t)
    .orderBy(o -> o.desc(t.col(Pet::getPrice).expr()))
    .build();
```

## Pagination

```java
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
    .from(t)
    .page(1, 20)  // page 1, 20 items per page
    .build();
```

## Joins

```java
var pet = dsl.table(Pet.class).as("pet");
var owner = dsl.table(Owner.class).as("o");

SelectStmt stmt = dsl.select(
        Dsl.item(pet.col(Pet::getId).expr()),
        Dsl.item(pet.col(Pet::getName).expr()),
        Dsl.item(owner.col(Owner::getName).expr())
    )
    .from(pet)
    .leftJoin(owner).on(owner.col(Owner::getId).eq(pet.col(Pet::getOwnerId)))
    .where(w -> w.and(pet.col(Pet::getAvailable).eq(true)))
    .build();
```

## Group By & Having

```java
var t = dsl.table(Order.class).as("o");

SelectStmt stmt = dsl.select(
        Dsl.item(t.col(Order::getStatus).expr()),
        Dsl.item(Dsl.function("count", Dsl.literal(1)).as("count"))
    )
    .from(t)
    .groupBy(t.col(Order::getStatus))
    .having(h -> h.and(Dsl.function("count", Dsl.literal(1)).gt(10)))
    .build();
```

## Insert

```java
var t = dsl.table(Pet.class);

InsertStmt stmt = dsl.insertInto(t)
    .columns(Pet::getName, Pet::getSpecies, Pet::getPrice)
    .row("Fluffy", "Cat", new BigDecimal("599.99"))
    .build();

db.execute(Command.of(lumen.renderer().render(stmt, Bindings.empty())));
```

## Update

```java
var t = dsl.table(Pet.class).as("p");

UpdateStmt stmt = dsl.update(t)
    .set(Pet::getPrice, new BigDecimal("699.99"))
    .set(Pet::getAvailable, false)
    .where(t.col(Pet::getId).eq(1L))
    .build();

db.execute(Command.of(lumen.renderer().render(stmt, Bindings.empty())));
```

## Delete

```java
var t = dsl.table(Pet.class);

DeleteStmt stmt = dsl.deleteFrom(t)
    .where(t.col(Pet::getId).eq(1L))
    .build();

db.execute(Command.of(lumen.renderer().render(stmt, Bindings.empty())));
```

## Logical Delete

See [Logical Delete](logical-delete.md) for details.

```java
var t = dsl.table(Pet.class);

UpdateStmt stmt = dsl.logicalDeleteFrom(t)
    .where(t.col(Pet::getId).eq(1L))
    .build();
```

## Functions

```java
import static io.lighting.lumen.dsl.Dsl.function;
import static io.lighting.lumen.dsl.Dsl.literal;

var t = dsl.table(Order.class).as("o");

SelectStmt stmt = dsl.select(
        Dsl.item(t.col(Order::getId).expr()),
        Dsl.item(function("coalesce", t.col(Order::getDiscount), literal(0)).as("effective_discount"))
    )
    .from(t)
    .build();
```

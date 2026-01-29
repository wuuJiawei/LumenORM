# Quick Start

Learn LumenORM in 5 minutes.

## Installation

Add Maven dependency:

[![](https://img.shields.io/maven-central/v/pub.lighting/lumen-core?color=blue&style=flat-square)](https://central.sonatype.com/artifact/pub.lighting/lumen-core)

```xml
<dependency>
    <groupId>pub.lighting</groupId>
    <artifactId>lumen-core</artifactId>
    <version>最新版本</version>
</dependency>
```

For Spring Boot 3:

```xml
<dependency>
    <groupId>pub.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

## Define Entity

```java
@Table(name = "pets")
public class Pet {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "species")
    private String species;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "available")
    private Boolean available;

    // getters and setters
}
```

## Query with DSL (Type-Safe)

```java
// Create DSL instance
Dsl dsl = new Dsl(entityMetaRegistry);

// Build query
var t = dsl.table(Pet.class).as("p");

SelectStmt stmt = dsl.select(
        Dsl.item(t.col(Pet::getId).expr()),
        Dsl.item(t.col(Pet::getName).expr()),
        Dsl.item(t.col(Pet::getPrice).expr())
    )
    .from(t)
    .where(w -> w.and(t.col(Pet::getAvailable).eq(true)))
    .orderBy(o -> o.desc(t.col(Pet::getPrice).expr()))
    .page(1, 10)
    .build();

// Execute
List<Pet> pets = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
```

## Query with Templates (Native SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    LIMIT ? OFFSET ?
    """;

RenderedSql rendered = db.getRenderer().render(
    Query.of(sql),
    Bindings.of("pageSize", 10, "offset", 0)
);

List<Pet> pets = db.fetch(Query.of(rendered), Pet.class);
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

## Run Examples

```bash
# Todo app
mvn -pl example/todo-example spring-boot:run

# Pet store demo
mvn -pl example/pet-store spring-boot:run
```

## Next Steps

- [DSL Guide](dsl-guide.md) - Type-safe query building
- [Template Guide](template-guide.md) - SQL text block patterns
- [Entity Definition](entity-definition.md) - Annotations reference
- [APT Guide](apt-guide.md) - Compile-time validation

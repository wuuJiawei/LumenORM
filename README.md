# LumenORM

Love SQL, hate XML? A fresh choice for Java developers.

[![Java Version](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## Overview

LumenORM is a lightweight SQL-First ORM for Java with dual query entry points:

- **Fluent DSL** - Type-safe query builder
- **Text Block Templates** - Native SQL with dynamic directives

**No external ORM dependencies.** Just JDBC.

## Features

- Dual Query Entry Points (DSL + Templates)
- Type-Safe DSL with Lambda References
- Template Directives (@if, @for, @where, @in, @page, @orderBy)
- Entity Metadata (Reflection or APT-generated)
- Logical Deletion Support
- Active Record Pattern
- Batch Operations
- Spring Boot 3/4 Integration
- Solon Framework Integration
- Minimal Dependencies

## Quick Start

```bash
mvn clean install -DskipTests
```

Run examples:
```bash
mvn -pl example/todo-example spring-boot:run  # http://localhost:8080
mvn -pl example/pet-store spring-boot:run     # http://localhost:8081
```

## Two Ways to Query

### 1. Fluent DSL (Type-Safe)

```java
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

List<Pet> pets = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
```

### 2. Text Block Templates (Native SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## Entity Definition

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
}
```

## Documentation

- [Quick Start](docs/quick-start.md) - Get started in 5 minutes
- [DSL Guide](docs/dsl-guide.md) - Type-safe query building
- [Template Guide](docs/template-guide.md) - SQL text block patterns
- [Entity Definition](docs/entity-definition.md) - Annotations reference
- [APT Guide](docs/apt-guide.md) - Compile-time validation
- [Logical Delete](docs/logical-delete.md) - Soft delete support
- [Transactions](docs/transactions.md) - Transaction management
- [Batch Operations](docs/batch-operations.md) - Bulk inserts/updates
- [Active Record](docs/active-record.md) - Active Record pattern
- [Spring Boot](docs/spring-boot-integration.md) - Spring Boot integration
- [Solon](docs/solon-integration.md) - Solon framework integration
- [Examples](docs/examples.md) - Example projects

## Project Structure

```
LumenORM/
├── lumen-core/                    # Core ORM engine
├── lumen-spring-boot-starter/     # Spring Boot 3 integration
├── lumen-spring-boot-4-starter/   # Spring Boot 4 integration
├── lumen-solon-plugin/            # Solon framework integration
├── docs/                          # Documentation
└── example/
    ├── core-example/              # Core API examples
    ├── todo-example/              # Todo REST API demo
    └── pet-store/                 # Pet store demo
```

## Dependencies

**Spring Boot 3:**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Core Only:**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## License

MIT

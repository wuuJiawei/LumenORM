# LumenORM

Love SQL, hate XML? A fresh choice for Java developers.

[![Java Version](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## Overview

LumenORM is a lightweight SQL-First ORM for Java with **three query entry points**:

- **Interface-based SQL** - Define SQL in interfaces with `@SqlTemplate`, validated at compile-time
- **Fluent DSL** - Type-safe query builder with lambda references
- **Text Block Templates** - Native SQL with dynamic directives

**No external ORM dependencies.** Just JDBC.

## Why LumenORM?

```java
// Define SQL in your interface - validated at compile-time!
public interface PetRepository extends SqlTemplate {

    @SqlTemplate("""
        SELECT id, name, price
        FROM pets
        WHERE species = #{species}
        AND available = true
        ORDER BY price DESC
        """)
    List<Pet> findAvailableBySpecies(String species);

    // Built-in functions like #{now()}, #{uuid()} supported
    @SqlTemplate("""
        INSERT INTO pets (name, species, price, created_at)
        VALUES (#{name}, #{species}, #{price}, #{now()})
        """)
    void insert(Pet pet);
}

// Usage - just call the method!
List<Pet> dogs = petRepository.findAvailableBySpecies("dog");
```

No XML. No string concatenation. **Pure SQL in Java.**

## Features

- Interface-based SQL with `@SqlTemplate` and compile-time validation
- Built-in template functions (`#{now()}`, `#{uuid()}`, `#{random()}`)
- Type-safe Fluent DSL with lambda references
- Custom template functions via `TemplateFunction`
- Dual query entry points (Interface + DSL + Templates)
- Entity metadata (Reflection or APT-generated)
- Logical deletion, Active Record, Batch operations
- Spring Boot 3/4 & Solon integration
- Minimal dependencies

## Quick Start

```bash
mvn clean install -DskipTests
```

Run examples:
```bash
mvn -pl example/todo-example spring-boot:run  # http://localhost:8080
mvn -pl example/pet-store spring-boot:run     # http://localhost:8081
```

## Three Ways to Query

### 1. Interface-based SQL (Compile-time Validated)

```java
public interface PetRepository extends SqlTemplate {

    @SqlTemplate("""
        SELECT id, name, price
        FROM pets
        WHERE species = #{species}
        AND available = true
        """)
    List<Pet> findAvailableBySpecies(String species);
}

// Spring injection - just use it!
@Autowired
PetRepository petRepository;

List<Pet> pets = petRepository.findAvailableBySpecies("cat");
```

### 2. Fluent DSL (Type-Safe)

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

### 3. Text Block Templates (Native SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## Template Functions

Built-in functions available in `@SqlTemplate`:

```java
@SqlTemplate("""
    INSERT INTO pets (id, name, created_at, track_id)
    VALUES (#{id}, #{name}, #{now()}, #{uuid()})
    """)
void insert(Pet pet);

// Custom functions
@SqlTemplate("""
    SELECT * FROM pets
    WHERE name LIKE #{like(#{name})}
    """)
List<Pet> searchByName(String name);
```

## Documentation

- [Quick Start](docs/quick-start.md) - Get started in 5 minutes
- [APT Guide](docs/apt-guide.md) - Interface-based SQL with @SqlTemplate
- [DSL Guide](docs/dsl-guide.md) - Type-safe query building
- [Template Guide](docs/template-guide.md) - SQL text block patterns
- [Entity Definition](docs/entity-definition.md) - Annotations reference
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

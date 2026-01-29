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
        WHERE available = true
        @if(#{species}) {
            AND species = #{species}
        }
        @if(#{minPrice}) {
            AND price >= #{minPrice}
        }
        @if(#{maxPrice}) {
            AND price <= #{maxPrice}
        }
        ORDER BY price DESC
        """)
    List<Pet> search(PetSearchCriteria criteria);

    // Built-in functions like #{now()}, #{uuid()} supported
    @SqlTemplate("""
        INSERT INTO pets (name, species, price, created_at)
        VALUES (#{name}, #{species}, #{price}, #{now()})
        """)
    void insert(Pet pet);
}

// Usage - just call the method!
List<Pet> pets = petRepository.search(new PetSearchCriteria("cat", 10.0, 100.0));
```

No XML. No string concatenation. **Pure SQL in Java with dynamic directives.**

## Key Features

### 1. Dynamic SQL with `@if` and `@for`

```java
@SqlTemplate("""
    SELECT * FROM pets WHERE 1=1
    @if(#{name}) {
        AND name = #{name}
    }
    @if(#{tags} && #{tags.length} > 0) {
        AND id IN (
            @for((tag, index) in #{tags}) {
                #{tag.id}@{if(index < tags.length - 1)}, @end{}
            }
        )
    }
    """)
List<Pet> findByCondition(PetCondition condition);
```

### 2. Built-in Template Directives

| Directive | Description |
|-----------|-------------|
| `@if(cond) { ... }` | Conditional SQL blocks |
| `@for((item, index) in list) { ... }` | Loop over collections |
| `@where { ... }` | Auto WHERE/AND handling |
| `@in(list) { ... }` | IN clause generation |
| `@orderBy(field) { ... }` | Safe ORDER BY |
| `@page(page, size) { ... }` | Pagination |

### 3. Built-in Functions

| Function | Description |
|----------|-------------|
| `#{now()}` | Current timestamp |
| `#{uuid()}` | UUID generation |
| `#{random()}` | Random value |
| `#{like(value)}` | LIKE pattern |
| `#{upper(value)}` | UPPER case |
| `#{lower(value)}` | Lower case |

## Features

- Interface-based SQL with `@SqlTemplate` and compile-time validation
- Dynamic SQL directives (`@if`, `@for`, `@where`, `@in`, `@page`, `@orderBy`)
- Built-in template functions (`#{now()}`, `#{uuid()}`, etc.)
- Custom template functions via `TemplateFunction`
- Type-safe Fluent DSL with lambda references
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
        ORDER BY price DESC
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

## Dynamic SQL Examples

### Conditional WHERE with `@if`

```java
@SqlTemplate("""
    SELECT * FROM pets WHERE 1=1
    @if(#{name}) {
        AND name = #{name}
    }
    @if(#{species}) {
        AND species = #{species}
    }
    @if(#{minPrice}) {
        AND price >= #{minPrice}
    }
    """)
List<Pet> search(String name, String species, BigDecimal minPrice);
```

### IN Clause with `@for`

```java
@SqlTemplate("""
    SELECT * FROM pets WHERE id IN (
        @for((id, index) in #{ids}) {
            #{id}@{if(index < ids.length - 1)}, @end{}
        }
    )
    """)
List<Pet> findByIds(List<Long> ids);
```

### Safe ORDER BY with `@orderBy`

```java
@SqlTemplate("""
    SELECT * FROM pets
    @orderBy(#{sortBy}) {
        ORDER BY #{sortBy} #{sortDir}
    }
    """)
List<Pet> findAll(String sortBy, String sortDir);
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

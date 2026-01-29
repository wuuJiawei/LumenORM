# LumenORM - SQL-First Java ORM

A lightweight SQL-First ORM framework for Java with dual query entry points and zero external ORM dependencies.

## Quick Start

### Prerequisites
- Java 17 or later
- Maven 3.6+

### Build

```bash
export JAVA_HOME=$(/usr/lib/jvm/java-21-openjdk-amd64)
mvn clean install -DskipTests
```

### Run Examples

**Todo App (Spring Boot 3):**
```bash
mvn -pl example/todo-example spring-boot:run
# Visit http://localhost:8080
```

**Pet Store Demo:**
```bash
mvn -pl example/pet-store spring-boot:run
# Visit http://localhost:8081
```

**DSL vs Template Showcase:**
```bash
mvn -pl example/todo-example spring-boot:run
# Visit http://localhost:8080/showcase
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

List<Pet> results = db.fetch(Query.of(stmt, Bindings.empty()), Pet.class);
```

### 2. Text Block Templates (Native SQL)

```java
String sql = """
    SELECT p.id, p.name, p.price
    FROM pets p
    WHERE p.available = true
    ORDER BY p.price DESC
    LIMIT ? OFFSET ?
    """;

RenderedSql rendered = db.getRenderer().render(
    Query.of(sql),
    Bindings.of("pageSize", 10, "offset", 0)
);
```

## Project Structure

```
LumenORM/
├── lumen-core/                    # Core ORM engine
├── lumen-spring-boot-starter/     # Spring Boot 3 integration
├── lumen-spring-boot-4-starter/   # Spring Boot 4 integration
├── lumen-solon-plugin/            # Solon framework integration
├── example/
│   ├── core-example/              # Core API examples
│   ├── todo-example/              # Todo REST API demo
│   └── pet-store/                 # Pet store demo with DSL patterns
└── README.md                      # Full design documentation
```

## Features

- **Dual Query Entry Points**: DSL builder or Text Block templates
- **Type-Safe DSL**: Method references and compile-time validation
- **Template Directives**: `@if`, `@for`, `@where`, `@in`, `@page`
- **Entity Metadata**: Reflection-based or APT-generated
- **Logical Deletion**: Built-in `@LogicDelete` support
- **Multi-Framework**: Spring Boot 3/4 and Solon support
- **Minimal Dependencies**: No external ORM, just JDBC

## Dependencies

Add to your `pom.xml`:

**Spring Boot 3:**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Spring Boot 4:**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-spring-boot-4-starter</artifactId>
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
    // getters and setters
}
```

## License

MIT

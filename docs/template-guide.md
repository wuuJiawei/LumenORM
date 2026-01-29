# Template Guide

Write native SQL with text blocks and dynamic directives.

## Basic Template

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

RenderedSql rendered = db.getRenderer().render(
    Query.of(sql),
    Bindings.empty()
);
```

## Parameters

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = :available
    AND species = :species
    """;

RenderedSql rendered = db.getRenderer().render(
    Query.of(sql),
    Bindings.of("available", true, "species", "Cat")
);
```

## @if Directive

```java
String sql = """
    SELECT id, name, price
    FROM pets
    @if(includeInStock) {
        WHERE available = true
    }
    ORDER BY price DESC
    """;

// Rendered with includeInStock=true:
// SELECT id, name, price FROM pets WHERE available = true ORDER BY price DESC
```

## @for Directive

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE species IN @for(s : species) { @or :s }
    """;

// Rendered with species=["Cat", "Dog"]:
// SELECT id, name, price FROM pets WHERE species IN (?, ?) -- binds: ["Cat", "Dog"]
```

## @where Directive

```java
String sql = """
    SELECT id, name, price
    FROM pets
    @where {
        @if(available != null) { available = :available }
        @if(species != null) { AND species = :species }
        @if(minPrice != null) { AND price >= :minPrice }
    }
    """;

// Automatically handles AND/OR prefixes and empty blocks
```

## @in Directive

```java
String sql = """
    SELECT id, name
    FROM pets
    WHERE species IN @in(species)
    """;

// Safe handling of empty lists (configurable strategy)
```

## @orderBy Directive

```java
String sql = """
    SELECT id, name, price
    FROM pets
    @orderBy(:sort, allowed = {
        PRICE_DESC : price DESC,
        PRICE_ASC : price ASC,
        NAME_ASC : name ASC
    }, default = PRICE_DESC)
    """;

// Only allows whitelisted sort options, prevents SQL injection
```

## @page Directive

```java
String sql = """
    SELECT id, name, price
    FROM pets
    ORDER BY price DESC
    @page(:page, :pageSize)
    """;

// Renders as LIMIT/OFFSET for MySQL, FETCH FIRST for PostgreSQL, etc.
```

## @table and @col Macros

```java
String sql = """
    SELECT @col(Pet::id), @col(Pet::name), @col(Pet::price)
    FROM @table(Pet) p
    WHERE p.@col(Pet::available) = true
    """;

// Automatically uses correct column names from @Column annotations
```

## @having Directive

```java
String sql = """
    SELECT status, COUNT(*) as count
    FROM orders
    GROUP BY status
    @having {
        @if(minCount != null) { COUNT(*) >= :minCount }
    }
    """
```

## Dialect-Specific SQL

```java
String sql = """
    SELECT id, name FROM pets
    @if(::dialect == 'mysql') { LIMIT 1 }
    @if(::dialect == 'oracle') { ROWNUM <= 1 }
    """;

// Use ::dialect variable for database-specific syntax
```

## @SqlTemplate Annotation

Use `@SqlTemplate` for compile-time validation:

```java
public interface PetRepository {

    @SqlTemplate("""
        SELECT id, name, price
        FROM pets
        WHERE available = :available
        @if(species != null) { AND species = :species }
        @orderBy(:sort, allowed = { PRICE_DESC : price DESC }, default = PRICE_DESC)
        @page(:page, :pageSize)
        """)
    List<Pet> findPets(
        @Bind("available") boolean available,
        @Bind("species") String species,
        @Bind("sort") String sort,
        @Bind("page") int page,
        @Bind("pageSize") int pageSize,
        RowMapper<Pet> mapper
    );
}
```

## @SqlConst for Reusable Templates

```java
public class PetQueries {

    @SqlConst
    static final String FIND_AVAILABLE = """
        SELECT id, name, price
        FROM pets
        WHERE available = true
        """;

    @SqlConst
    static final String FIND_BY_SPECIES = """
        SELECT id, name, price
        FROM pets
        WHERE species = :species
        """;
}

// Use with db.run() - still validated at compile time
List<Pet> pets = db.run(PetQueries.FIND_BY_SPECIES,
    Bindings.of("species", "Cat"), Pet.class);
```

## Security Features

- All `:param` values are bound as `?` parameters (no SQL injection)
- Column/table names must come from `@table`/`@col` macros or `@orderBy` whitelist
- `RawSql` fragments require explicit opt-in

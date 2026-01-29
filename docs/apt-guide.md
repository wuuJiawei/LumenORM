# APT Guide

Use Annotation Processing for compile-time SQL validation.

## @SqlTemplate Annotation

Apply `@SqlTemplate` to interface methods for compile-time validation:

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

## @SqlConst Annotation

Mark reusable SQL constants for compile-time validation:

```java
public class PetQueries {

    @SqlConst
    static final String FIND_AVAILABLE = """
        SELECT id, name, price
        FROM pets
        WHERE available = true
        """;

    @SqlConst
    static final String FIND_BY_ID = """
        SELECT id, name, price
        FROM pets
        WHERE id = :id
        """;
}
```

## How APT Works

1. **Parsing**: APT parses the template syntax
2. **Validation**: Checks parameters, directives, and SQL structure
3. **Code Generation**: Generates implementation class
4. **Error Reporting**: Reports errors at compile time

## Validation Rules

### Parameter Validation

- All `:param` references must have corresponding `@Bind` parameter
- Parameter types must be compatible with usage

```java
// Valid
@SqlTemplate("WHERE id = :id")
List<Pet> findById(@Bind("id") Long id, RowMapper<Pet> mapper);

// Invalid - :id has no @Bind
@SqlTemplate("WHERE id = :id")
List<Pet> findById(RowMapper<Pet> mapper);  // Compile error!
```

### @orderBy Validation

```java
// Valid - default is in allowed list
@SqlTemplate("""
    SELECT * FROM pets
    @orderBy(:sort, allowed = { PRICE_DESC : price DESC }, default = PRICE_DESC)
    """)
List<Pet> findPets(@Bind("sort") String sort, RowMapper<Pet> mapper);

// Invalid - default not in allowed
@SqlTemplate("""
    SELECT * FROM pets
    @orderBy(:sort, allowed = { PRICE_DESC : price DESC }, default = NAME_ASC)
    """)  // Compile error!
List<Pet> findPets(@Bind("sort") String sort, RowMapper<Pet> mapper);
```

### @page Validation

```java
// Required for PageResult return type
@SqlTemplate("SELECT * FROM pets @page(:page, :pageSize)")
PageResult<Pet> findPets(
    @Bind("page") int page,
    @Bind("pageSize") int pageSize,
    RowMapper<Pet> mapper
);
```

## Generated Code

APT generates implementation classes:

```java
// PetRepository_Impl generated for PetRepository interface
public final class PetRepository_Impl implements PetRepository {
    private final Db db;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;

    public PetRepository_Impl(Db db, Dialect dialect, EntityMetaRegistry metaRegistry) {
        this.db = db;
        this.dialect = dialect;
        this.metaRegistry = metaRegistry;
    }

    @Override
    public List<Pet> findPets(boolean available, String species, String sort,
                              int page, int pageSize, RowMapper<Pet> mapper) {
        Bindings bindings = new Bindings()
            .bind("available", available)
            .bindIfNotNull("species", species)
            .bind("sort", sort)
            .bind("page", page)
            .bind("pageSize", pageSize);

        RenderedSql rs = template.render(bindings, dialect);
        return db.fetch(Query.of(rs), mapper);
    }
}
```

## Enable APT in Maven

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>pub.lighting</groupId>
                        <artifactId>lumen-core</artifactId>
                        <version>${project.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Benefits

- **Early Error Detection**: Catch SQL errors at compile time
- **Type Safety**: Parameter types validated at compile time
- **Performance**: No runtime template parsing
- **IDE Support**: Better code completion and navigation

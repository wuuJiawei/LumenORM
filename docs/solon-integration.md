# Solon Integration

Use LumenORM with the Solon framework.

## Add Dependency

[![](https://img.shields.io/maven-central/v/pub.lighting/lumen-solon-plugin?color=blue&style=flat-square)](https://central.sonatype.com/artifact/pub.lighting/lumen-solon-plugin)

```xml
<dependency>
    <groupId>pub.lighting</groupId>
    <artifactId>lumen-solon-plugin</artifactId>
    <version>最新版本</version>
</dependency>
```

## Register Plugin

```java
import org.noear.solon.Solon;

public class App {
    public static void main(String[] args) {
        Solon.start(App.class, args, app -> {
            // Plugin auto-registers LumenORM beans
        });
    }
}
```

## Inject Dependencies

```java
@Singleton
public class PetService {

    @Inject
    Lumen lumen;

    @Inject
    Db db;

    public List<Pet> findAll() throws SQLException {
        return db.fetch(
            Query.of("SELECT * FROM pets"),
            RowMappers.auto(Pet.class)
        );
    }
}
```

## Configuration

```yaml
# app.yml
spring:
  datasource:
    url: jdbc:h2:mem:mydb
    driver: org.h2.Driver
    username: sa
    password:

lumen:
  sql:
    log-enabled: true
    log-mode: SEPARATE
```

## Auto-Configured Beans

The plugin provides:

- `Lumen` - Main LumenORM instance
- `Db` - Database access
- `Dsl` - DSL builder
- `EntityMetaRegistry` - Entity metadata
- `Dialect` - SQL dialect
- `SqlRenderer` - SQL renderer
- `EntityNameResolver` - Entity name resolution

## Example Service

```java
@Singleton
public class PetService {

    private final Lumen lumen;
    private final Dsl dsl;

    public PetService(Lumen lumen) {
        this.lumen = lumen;
        this.dsl = lumen.dsl();
    }

    public List<Pet> findAvailable() throws SQLException {
        var t = dsl.table(Pet.class).as("p");
        SelectStmt stmt = dsl.select(Dsl.item(t.col(Pet::getId).expr()))
            .from(t)
            .where(w -> w.and(t.col(Pet::getAvailable).eq(true)))
            .build();
        return lumen.db().fetch(Query.of(stmt, Bindings.empty()), Pet.class);
    }
}
```

## Plugin Class

```java
@Configuration
public class LumenSolonPlugin {

    @Bean
    public EntityNameResolver entityNameResolver() {
        return EntityNameResolvers.auto();
    }

    @Bean
    public EntityMetaRegistry entityMetaRegistry() {
        return new ReflectionEntityMetaRegistry();
    }

    @Bean
    public Dialect dialect(DataSource dataSource) {
        return DialectResolver.resolve(dataSource);
    }

    @Bean
    public SqlRenderer sqlRenderer(Dialect dialect) {
        return new SqlRenderer(dialect);
    }

    @Bean
    public Lumen lumen(DataSource ds, Dialect d, EntityMetaRegistry r) {
        return Lumen.builder()
            .dataSource(ds)
            .dialect(d)
            .metaRegistry(r)
            .build();
    }

    @Bean
    public Db db(Lumen lumen) {
        return lumen.db();
    }
}
```

## Benefits

- Lightweight integration
- No Spring dependency
- Fast startup
- Small footprint

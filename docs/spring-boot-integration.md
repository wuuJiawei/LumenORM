# Spring Boot Integration

Use LumenORM with Spring Boot 3 or 4.

## Add Dependency

Spring Boot 3:

[![](https://img.shields.io/maven-central/v/pub.lighting/lumen-spring-boot-starter?color=blue&style=flat-square)](https://central.sonatype.com/artifact/pub.lighting/lumen-spring-boot-starter)

```xml
<dependency>
    <groupId>pub.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

Spring Boot 4:

[![](https://img.shields.io/maven-central/v/pub.lighting/lumen-spring-boot-4-starter?color=blue&style=flat-square)](https://central.sonatype.com/artifact/pub.lighting/lumen-spring-boot-4-starter)

```xml
<dependency>
    <groupId>pub.lighting</groupId>
    <artifactId>lumen-spring-boot-4-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

## Configuration

```properties
# application.properties
spring.datasource.url=jdbc:h2:mem:mydb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# LumenORM SQL logging
lumen.sql.log-enabled=true
lumen.sql.log-mode=SEPARATE
```

## Auto-Configuration

Spring Boot auto-configures:

- `Lumen` instance
- `Db` instance
- `Dsl` instance
- `EntityMetaRegistry`
- `Dialect`
- `SqlRenderer`

## Inject Lumen

```java
@Service
public class PetService {

    private final Lumen lumen;

    public PetService(Lumen lumen) {
        this.lumen = lumen;
    }

    public List<Pet> findAll() throws SQLException {
        return lumen.db().fetch(
            Query.of("SELECT * FROM pets"),
            RowMappers.auto(Pet.class)
        );
    }
}
```

## Inject Db Directly

```java
@Service
public class OrderService {

    private final Db db;

    public OrderService(Db db) {
        this.db = db;
    }

    public void createOrder(Order order) throws SQLException {
        // Use db directly
    }
}
```

## Custom Configuration

```java
@Configuration
public class LumenConfig {

    @Bean
    public LumenProperties lumenProperties() {
        return new LumenProperties();
    }

    @Bean
    public Lumen lumen(LumenProperties props, DataSource ds) {
        return Lumen.builder()
            .dataSource(ds)
            .build();
    }
}
```

## SQL Logging

```java
@Configuration
public class SqlLogConfig {

    @Bean
    public SqlLog sqlLog() {
        return SqlLog.builder()
            .mode(SqlLog.Mode.SEPARATE)  // or INLINE
            .includeElapsed(true)
            .includeRowCount(true)
            .build();
    }
}
```

## Transaction Management

```java
@Service
public class OrderService {

    @Autowired
    private Lumen lumen;

    @Transactional
    public void createOrder(Order order) throws SQLException {
        Db db = lumen.db();
        db.execute(Command.of(insertOrder));
        db.execute(Command.of(updateInventory));
    }
}
```

## Running the Example

```bash
mvn -pl example/todo-example spring-boot:run
# http://localhost:8080
```

## Module Structure

```
lumen-spring-boot-starter/
├── pom.xml
├── src/main/java/io/lighting/lumen/starter/
│   ├── LumenAutoConfiguration.java    # Main auto-config
│   ├── LumenDaoAutoConfiguration.java # DAO scanning
│   └── LumenProperties.java          # Configuration properties
└── src/main/resources/
    └── META-INF/spring/...
```

## Features

- Automatic bean registration
- Configuration properties binding
- SQL logging support
- Transaction integration
- DAO scanning support

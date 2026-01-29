# LumenORM

Java 专属的精彩 SQL-First ORM 库。

[![Java 版本](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## 概述

LumenORM 是一款轻量级的 SQL-First Java ORM，支持双查询入口：

- **Fluent DSL** - 类型安全的查询构建器
- **Text Block Templates** - 原生 SQL 动态模板

**无外部 ORM 依赖。** 仅依赖 JDBC。

## 特性

- 双查询入口 (DSL + 模板)
- 基于 Lambda 引用的类型安全 DSL
- 模板指令 (@if, @for, @where, @in, @page, @orderBy)
- 实体元数据 (反射或 APT 生成)
- 逻辑删除支持
- Active Record 模式
- 批量操作
- Spring Boot 3/4 集成
- Solon 框架集成
- 最小依赖

## 快速开始

```bash
mvn clean install -DskipTests
```

运行示例：
```bash
mvn -pl example/todo-example spring-boot:run  # http://localhost:8080
mvn -pl example/pet-store spring-boot:run     # http://localhost:8081
```

## 两种查询方式

### 1. Fluent DSL (类型安全)

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

### 2. Text Block Templates (原生 SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## 实体定义

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

## 文档

- [快速开始](docs/quick-start.md) - 5 分钟入门
- [DSL 指南](docs/dsl-guide.md) - 类型安全查询构建
- [模板指南](docs/template-guide.md) - SQL 文本块模式
- [实体定义](docs/entity-definition.md) - 注解参考
- [APT 指南](docs/apt-guide.md) - 编译时验证
- [逻辑删除](docs/logical-delete.md) - 软删除支持
- [事务管理](docs/transactions.md) - 事务管理
- [批量操作](docs/batch-operations.md) - 批量插入/更新
- [Active Record](docs/active-record.md) - Active Record 模式
- [Spring Boot 集成](docs/spring-boot-integration.md) - Spring Boot 集成
- [Solon 集成](docs/solon-integration.md) - Solon 框架集成
- [示例项目](docs/examples.md) - 示例项目

## 项目结构

```
LumenORM/
├── lumen-core/                    # 核心 ORM 引擎
├── lumen-spring-boot-starter/     # Spring Boot 3 集成
├── lumen-spring-boot-4-starter/   # Spring Boot 4 集成
├── lumen-solon-plugin/            # Solon 框架集成
├── docs/                          # 文档
└── example/
    ├── core-example/              # 核心 API 示例
    ├── todo-example/              # Todo REST API 示例
    └── pet-store/                 # 宠物商店示例
```

## 依赖配置

**Spring Boot 3：**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**仅核心库：**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 许可证

MIT

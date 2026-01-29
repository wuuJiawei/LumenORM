# LumenORM

厌倦 MyBatis XML？LumenORM 给 Javaer 的新选择。

[![Java 版本](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## 概述

LumenORM 是一款轻量级的 SQL-First Java ORM，支持**三种查询入口**：

- **接口定义 SQL** - 通过 `@SqlTemplate` 在接口中定义 SQL，编译时验证
- **Fluent DSL** - 基于 Lambda 的类型安全查询构建器
- **Text Block Templates** - 原生 SQL 动态模板

**无外部 ORM 依赖。** 仅依赖 JDBC。

## 为什么选择 LumenORM？

```java
// 在接口中定义 SQL - 编译时验证！
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

    // 支持内置函数 #{now()}、#{uuid()} 等
    @SqlTemplate("""
        INSERT INTO pets (name, species, price, created_at)
        VALUES (#{name}, #{species}, #{price}, #{now()})
        """)
    void insert(Pet pet);
}

// 使用 - 直接调用方法即可！
List<Pet> pets = petRepository.search(new PetSearchCriteria("cat", 10.0, 100.0));
```

没有 XML。没有字符串拼接。**带动态指令的纯 SQL 在 Java 中。**

## 核心特性

### 1. 动态 SQL `@if` 和 `@for`

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

### 2. 内置模板指令

| 指令 | 描述 |
|------|------|
| `@if(cond) { ... }` | 条件 SQL 块 |
| `@for((item, index) in list) { ... }` | 循环遍历集合 |
| `@where { ... }` | 自动 WHERE/AND 处理 |
| `@in(list) { ... }` | IN 子句生成 |
| `@orderBy(field) { ... }` | 安全 ORDER BY |
| `@page(page, size) { ... }` | 分页 |

### 3. 内置函数

| 函数 | 描述 |
|------|------|
| `#{now()}` | 当前时间戳 |
| `#{uuid()}` | UUID 生成 |
| `#{random()}` | 随机值 |
| `#{like(value)}` | LIKE 模式 |
| `#{upper(value)}` | 大写转换 |
| `#{lower(value)}` | 小写转换 |

## 特性

- 接口定义 SQL + `@SqlTemplate` + 编译时验证
- 动态 SQL 指令 (`@if`, `@for`, `@where`, `@in`, `@page`, `@orderBy`)
- 内置模板函数 (`#{now()}`, `#{uuid()}`, 等)
- 自定义模板函数 `TemplateFunction`
- 基于 Lambda 的类型安全 Fluent DSL
- 实体元数据 (反射或 APT 生成)
- 逻辑删除、Active Record、批量操作
- Spring Boot 3/4 & Solon 集成
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

## 三种查询方式

### 1. 接口定义 SQL (编译时验证)

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

// Spring 注入 - 直接使用！
@Autowired
PetRepository petRepository;

List<Pet> pets = petRepository.findAvailableBySpecies("cat");
```

### 2. Fluent DSL (类型安全)

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

### 3. Text Block Templates (原生 SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## 动态 SQL 示例

### 条件 WHERE 用 `@if`

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

### IN 子句用 `@for`

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

### 安全 ORDER BY 用 `@orderBy`

```java
@SqlTemplate("""
    SELECT * FROM pets
    @orderBy(#{sortBy}) {
        ORDER BY #{sortBy} #{sortDir}
    }
    """)
List<Pet> findAll(String sortBy, String sortDir);
```

## 文档

- [快速开始](docs/quick-start.md) - 5 分钟入门
- [APT 指南](docs/apt-guide.md) - 接口定义 SQL 与 @SqlTemplate
- [DSL 指南](docs/dsl-guide.md) - 类型安全查询构建
- [模板指南](docs/template-guide.md) - SQL 文本块模式
- [实体定义](docs/entity-definition.md) - 注解参考
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

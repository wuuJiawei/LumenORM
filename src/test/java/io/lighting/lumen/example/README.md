# MySQL CRUD 示例（Docker）

这个包展示了当前 LumenORM 支持的多种 CRUD 写法。示例实现位于
`MysqlCrudExampleTest`，运行时依赖 `docker-compose.yml` 的 MySQL 容器。

## 环境准备

1. 启动 MySQL：
   `docker compose up -d mysql`
2. 示例使用的连接信息：
   - URL: `jdbc:mysql://localhost:3307/lumen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
   - User: `lumen`
   - Password: `lumen`
3. Schema：
   `docker/init/mysql/001_schema.sql` 会创建 `orders` 表。

## 覆盖的 CRUD 写法

### 1) SQL 常量 + 模板渲染（Insert）
- 使用 `@SqlConst` 声明编译期常量 SQL。
- 通过 `SqlTemplate.parse(...).render(TemplateContext)` 渲染 `@table`、`@col` 宏。
- 使用 `executeAndReturnGeneratedKey` 获取自增主键。

### 2) 模板指令（Select）
- `@where`、`@if`、`@for`、`@or`、`@in`、`@orderBy` 的用法见 `SELECT_TEMPLATE`。
- 通过 `db.fetch(Query.of(rendered), OrderEntity.class)` 触发自动映射。

### 3) DSL AST（Update）
- 用 `Dsl.update(...)` 构建 `UpdateStmt`。
- 用 `SqlRenderer` 渲染，再调用 `executeOptimistic` 确保只更新一行。

### 4) 原生 SQL + 手动绑定（Delete）
- 直接构造 `RenderedSql` 并使用 `Bind.Value` 手动绑定，适合与现有 SQL 直接对接。

## 自动映射与自定义类型

- `Db.fetch(Query, Class<T>)` 会使用 `RowMappers.auto(...)`。
- 支持注册自定义 JDBC 适配器（含泛型）：`JdbcTypeAdapters.register(new TypeRef<...>() { }, adapter)`。
- 内置 `List` / `Set` / `Map` 等泛型容器的适配。

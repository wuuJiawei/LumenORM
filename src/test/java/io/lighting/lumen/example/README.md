# MySQL CRUD Example (Docker)

This package shows several CRUD styles supported by LumenORM today.
The examples are implemented in `MysqlCrudExampleTest` and run against the
Docker MySQL container from `docker-compose.yml`.

## Setup

1. Start MySQL:
   `docker compose up -d mysql`
2. Connection info used by the example:
   - URL: `jdbc:mysql://localhost:3307/lumen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
   - User: `lumen`
   - Password: `lumen`
3. Schema:
   `docker/init/mysql/001_schema.sql` creates `orders`.

## CRUD Styles Covered

### 1) SQL Constants + Template Render (Insert)
- Use `@SqlConst` for compile-time constant SQL.
- Render with `SqlTemplate.parse(...).render(TemplateContext)` to resolve
  `@table` and `@col` macros.
- Execute with `executeAndReturnGeneratedKey` for the auto-increment id.

### 2) Template Directives (Select)
- `@where`, `@if`, `@for`, `@or`, `@in`, and `@orderBy` are shown in
  `SELECT_TEMPLATE`.
- Execute with `db.fetch(Query.of(rendered), OrderEntity.class)` to use
  auto-mapping.

### 3) DSL AST (Update)
- Use `Dsl.update(...)` to build an `UpdateStmt`.
- Render with `SqlRenderer`, then call `executeOptimistic` to enforce
  exactly one row updated.

### 4) Raw SQL + Manual Binds (Delete)
- Direct `RenderedSql` creation with `Bind.Value` is supported for minimal
  overhead or for interoperating with existing SQL.

## Auto Mapping and Custom Types

- `Db.fetch(Query, Class<T>)` uses `RowMappers.auto(...)`.
- You can register custom JDBC adapters (including generic types) via
  `JdbcTypeAdapters.register(new TypeRef<...>() { }, adapter)`.
- Built-ins cover `List`, `Set`, and `Map` with generic element types.

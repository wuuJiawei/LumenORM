# Examples Guide

Learn LumenORM through working examples.

## Running Examples

```bash
# Build all modules first
mvn clean install -DskipTests

# Run specific example
mvn -pl example/<example-name> spring-boot:run
```

## Todo Example

A complete Todo REST API demonstrating CRUD operations.

**Location:** `example/todo-example/`

**Features:**
- RESTful CRUD endpoints
- DSL queries with Lambda references
- Template queries with @SqlTemplate
- BaseDao CRUD operations
- Logical deletion
- Pagination

**Run:**
```bash
mvn -pl example/todo-example spring-boot:run
# http://localhost:8080
```

**Endpoints:**
- `GET /todos` - List todos with pagination
- `POST /todos` - Create todo
- `GET /todos/{id}` - Get todo
- `PUT /todos/{id}` - Update todo
- `DELETE /todos/{id}` - Delete todo

## Pet Store Example

A pet store demo showcasing DSL patterns.

**Location:** `example/pet-store/`

**Features:**
- Entity relationships (Pet, Owner, Category)
- DSL-based repositories
- Multi-table queries
- Dynamic filtering
- Sample data initialization

**Run:**
```bash
mvn -pl example/pet-store spring-boot:run
# http://localhost:8081
```

**Pages:**
- `/` - Pet listing
- `/owners` - Owner management

## DSL vs Template Showcase

Compare DSL and Template approaches side by side.

**Location:** `example/todo-example/` (same as todo example)

**Run:**
```bash
mvn -pl example/todo-example spring-boot:run
# http://localhost:8080/showcase
```

**Demonstrates:**
- Fluent DSL code vs Template code
- Generated SQL comparison
- Bound parameters
- Benefits of each approach

## Core Examples

Unit tests demonstrating core APIs.

**Location:** `example/core-example/`

**Tests:**
- `SqlTemplateH2ExampleTest` - Template examples
- `LambdaDslH2ExampleTest` - DSL examples
- `LumenH2ExampleTest` - Basic usage

**Run:**
```bash
mvn -pl example/core-example test
```

## Integration Tests

Full integration tests with real databases.

**Location:** `lumen-core/src/test/java/io/lighting/lumen/integration/`

**Tests:**
- `H2IntegrationTest` - H2 database tests
- `DockerComposeIntegrationTest` - Multi-database tests

## Test Categories

| Category | Location | Purpose |
|----------|----------|---------|
| Unit | `lumen-core/src/test/java/.../dsl/` | DSL API tests |
| Unit | `lumen-core/src/test/java/.../template/` | Template tests |
| Unit | `lumen-core/src/test/java/.../apt/` | APT validation tests |
| Integration | `lumen-core/src/test/java/.../integration/` | Real DB tests |
| Example | `example/` | Full application examples |

## Key Examples by Feature

### DSL Queries
- `DslDmlTest.java` - Insert/Update/Delete
- `FluentDslTest.java` - Select patterns
- `LogicalDeleteDslTest.java` - Logical delete

### Templates
- `SqlTemplateTest.java` - Template directives
- `SqlTemplateProcessorTest.java` - APT validation

### Active Record
- `ActiveRecordTest.java` - Active Record patterns

### Transactions
- `TransactionManagerTest.java` - Transaction examples

## Adding Examples

1. Create test class in appropriate location
2. Use H2 for simple examples
3. Use Docker for multi-database tests
4. Document key patterns in comments

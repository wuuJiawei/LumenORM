# LumenORM

MyBatis の XML が好きじゃない？Java 開発者の新しい選択肢。

[![Java バージョン](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## 概要

LumenORM は、**三つのクエリエントリポイント**を持つ軽量な SQL-First Java ORM です：

- **インターフェース SQL** - `@SqlTemplate` でインターフェースに SQL を定義、コンパイル時検証
- **Fluent DSL** - Lambda 参照による型安全なクエリビルダー
- **Text Block Templates** - ネイティブ SQL 動的テンプレート

**外部 ORM 依存なし。** JDBC のみ。

## なぜ LumenORM ？

```java
// インターフェースに SQL を定義 - コンパイル時に検証！
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

    // ビルトイン関数 #{now()}、#{uuid()} など対応
    @SqlTemplate("""
        INSERT INTO pets (name, species, price, created_at)
        VALUES (#{name}, #{species}, #{price}, #{now()})
        """)
    void insert(Pet pet);
}

// 使い方 - メソッドを呼ぶだけ！
List<Pet> pets = petRepository.search(new PetSearchCriteria("cat", 10.0, 100.0));
```

XML なし。文字列連結なし。**動的ディレクティブ付き Java 内の純粋な SQL。**

## 核心機能

### 1. 動的 SQL `@if` と `@for`

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

### 2. ビルトイン テンプレート ディレクティブ

| ディレクティブ | 説明 |
|----------------|------|
| `@if(cond) { ... }` | 条件 SQL ブロック |
| `@for((item, index) in list) { ... }` | コレクションのループ |
| `@where { ... }` | 自動 WHERE/AND 処理 |
| `@in(list) { ... }` | IN 句生成 |
| `@orderBy(field) { ... }` | 安全 ORDER BY |
| `@page(page, size) { ... }` | ページネーション |

### 3. ビルトイン関数

| 関数 | 説明 |
|------|------|
| `#{now()}` | 現在のタイムスタンプ |
| `#{uuid()}` | UUID 生成 |
| `#{random()}` | ランダム値 |
| `#{like(value)}` | LIKE パターン |
| `#{upper(value)}` | 大文字変換 |
| `#{lower(value)}` | 小文字変換 |

## 機能

- インターフェース SQL + `@SqlTemplate` + コンパイル時検証
- 動的 SQL ディレクティブ (`@if`, `@for`, `@where`, `@in`, `@page`, `@orderBy`)
- ビルトイン テンプレート関数 (`#{now()}`, `#{uuid()}`, など)
- カスタム テンプレート関数 `TemplateFunction`
- Lambda 参照による型安全な Fluent DSL
- エンティティメタデータ (リフレクションまたは APT 生成)
- 論理削除、Active Record、バッチ操作
- Spring Boot 3/4 & Solon 連携
- 最小依存

## クイックスタート

```bash
mvn clean install -DskipTests
```

サンプル実行：
```bash
mvn -pl example/todo-example spring-boot:run  # http://localhost:8080
mvn -pl example/pet-store spring-boot:run     # http://localhost:8081
```

## 三つのクエリ方法

### 1. インターフェース SQL (コンパイル時検証)

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

// Spring インジェクション - そのまま使用！
@Autowired
PetRepository petRepository;

List<Pet> pets = petRepository.findAvailableBySpecies("cat");
```

### 2. Fluent DSL (型安全)

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

### 3. Text Block Templates (ネイティブ SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## 動的 SQL 例

### 条件 WHERE `@if`

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

### IN 句 `@for`

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

### 安全 ORDER BY `@orderBy`

```java
@SqlTemplate("""
    SELECT * FROM pets
    @orderBy(#{sortBy}) {
        ORDER BY #{sortBy} #{sortDir}
    }
    """)
List<Pet> findAll(String sortBy, String sortDir);
```

## ドキュメント

- [クイックスタート](docs/quick-start.md) - 5 分で始める
- [APT ガイド](docs/apt-guide.md) - インターフェース SQL と @SqlTemplate
- [DSL ガイド](docs/dsl-guide.md) - 型安全クエリビルダー
- [テンプレートガイド](docs/template-guide.md) - SQL テキストブロックパターン
- [エンティティ定義](docs/entity-definition.md) - アノテーションリファレンス
- [論理削除](docs/logical-delete.md) - ソフト削除サポート
- [トランザクション](docs/transactions.md) - トランザクション管理
- [バッチ操作](docs/batch-operations.md) - 一括挿入/更新
- [Active Record](docs/active-record.md) - Active Record パターン
- [Spring Boot 連携](docs/spring-boot-integration.md) - Spring Boot 連携
- [Solon 連携](docs/solon-integration.md) - Solon フレームワーク連携
- [サンプル](docs/examples.md) - サンプルプロジェクト

## プロジェクト構成

```
LumenORM/
├── lumen-core/                    # コア ORM エンジン
├── lumen-spring-boot-starter/     # Spring Boot 3 連携
├── lumen-spring-boot-4-starter/   # Spring Boot 4 連携
├── lumen-solon-plugin/            # Solon フレームワーク連携
├── docs/                          # ドキュメント
└── example/
    ├── core-example/              # コア API サンプル
    ├── todo-example/              # Todo REST API デモ
    └── pet-store/                 # ペットストアデモ
```

## 依存関係

**Spring Boot 3：**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**コアのみ：**
```xml
<dependency>
    <groupId>io.lighting</groupId>
    <artifactId>lumen-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## ライセンス

MIT

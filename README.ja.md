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
        WHERE species = #{species}
        AND available = true
        ORDER BY price DESC
        """)
    List<Pet> findAvailableBySpecies(String species);

    // ビルトイン関数 #{now()}、#{uuid()} など対応
    @SqlTemplate("""
        INSERT INTO pets (name, species, price, created_at)
        VALUES (#{name}, #{species}, #{price}, #{now()})
        """)
    void insert(Pet pet);
}

// 使い方 - メソッドを呼ぶだけ！
List<Pet> dogs = petRepository.findAvailableBySpecies("dog");
```

XML なし。文字列連結なし。**Java 内で純粋な SQL。**

## 機能

- インターフェース SQL + `@SqlTemplate` + コンパイル時検証
- ビルトイン SQL 関数 (`#{now()}`、`#{uuid()}`、`#{random()}`)
- Lambda 参照による型安全な Fluent DSL
- カスタムテンプレート関数 `TemplateFunction`
- 三つのクエリエントリポイント (インターフェース + DSL + テンプレート)
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

## テンプレート関数

`@SqlTemplate` で使用可能なビルトイン関数：

```java
@SqlTemplate("""
    INSERT INTO pets (id, name, created_at, track_id)
    VALUES (#{id}, #{name}, #{now()}, #{uuid()})
    """)
void insert(Pet pet);

// カスタム関数
@SqlTemplate("""
    SELECT * FROM pets
    WHERE name LIKE #{like(#{name})}
    """)
List<Pet> searchByName(String name);
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

# LumenORM

Java 向けの素晴らしい SQL-First ORM ライブラリ。

[![Java バージョン](https://img.shields.io/badge/java-17%2B-blue)](https://www.oracle.com/java/technologies/)
[![Maven](https://img.shields.io/badge/maven-3.6%2B-green)](https://maven.apache.org/)

## 概要

LumenORM は、二つのクエリエントリポイントを持つ軽量な SQL-First Java ORM です：

- **Fluent DSL** - 型安全なクエリビルダー
- **Text Block Templates** - ネイティブ SQL 動的テンプレート

**外部 ORM 依存なし。** JDBC のみ。

## 機能

- 二つのクエリエントリポイント (DSL + テンプレート)
- Lambda 参照による型安全な DSL
- テンプレートディレクティブ (@if, @for, @where, @in, @page, @orderBy)
- エンティティメタデータ (リフレクションまたは APT 生成)
- 論理削除サポート
- Active Record パターン
- バッチ操作
- Spring Boot 3/4 連携
- Solon フレームワーク連携
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

## 二つのクエリ方法

### 1. Fluent DSL (型安全)

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

### 2. Text Block Templates (ネイティブ SQL)

```java
String sql = """
    SELECT id, name, price
    FROM pets
    WHERE available = true
    ORDER BY price DESC
    """;

List<Pet> pets = db.run(sql, Bindings.empty(), Pet.class);
```

## エンティティ定義

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

## ドキュメント

- [クイックスタート](docs/quick-start.md) - 5 分で始める
- [DSL ガイド](docs/dsl-guide.md) - 型安全クエリビルダー
- [テンプレートガイド](docs/template-guide.md) - SQL テキストブロックパターン
- [エンティティ定義](docs/entity-definition.md) - アノテーションリファレンス
- [APT ガイド](docs/apt-guide.md) - コンパイル時検証
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

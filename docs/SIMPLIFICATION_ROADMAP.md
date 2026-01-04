# 简化路线图（SQL-first，DSL-second）

## 目标

- 降低新用户的心智负担。
- SQL-first 为默认路径，DSL 为次要路径。
- APT 必须是可选项；运行时路径无需代码生成即可使用。
- 保留安全边界：值绑定 vs 标识符白名单。

## 暂不做的事

- 不做完整 ORM 生命周期与状态管理。
- 不做分页/引用之外的广泛方言翻译。
- IDE/编译器插件放到未来增强。

## 当前痛点

- 入口过多（ActiveRecord、Model、Db、模板、DSL），容易迷路。
- 初始化成本高（Db、Dialect、MetaRegistry、EntityNameResolver、observers）。
- APT 看起来像“必需品”，不符合轻量路径。
- 注解与模板包结构不统一。

## 目标使用体验（最小心智）

```java
Lumen lumen = Lumen.builder()
    .dataSource(dataSource)
    .dialect(dialect)
    .metaRegistry(new ReflectionEntityMetaRegistry())
    .entityNameResolver(EntityNameResolvers.from(Map.of(...)))
    .build();

OrderDao dao = lumen.dao(OrderDao.class);
Db db = lumen.db();
Dsl dsl = lumen.dsl(); // optional
```

## 简化后的核心范围

- `Lumen` 作为唯一启动入口，提供 `Db`、`Dsl`、`dao(Class<T>)`。
- `Db` + `SqlTemplate` 运行时渲染路径优先。
- DSL 仍在 core，但定位为“次要路径”。
- APT 拆出去作为可选模块。

## 结构原则（不做模块拆分）

- 统一放在 `lumen-core`，通过“使用路径”而不是“模块”来区分复杂度。
- APT 放在 core 内但保持可选（不依赖也能使用）。
- ActiveRecord 作为可选路径，保留但不作为默认入口（也可后续下线）。

## 可移除/可下沉功能

- ActiveRecord / Model 从主路径移出（`io.lighting.lumen.active.*`）。
- 任何依赖 APT 才能“基本使用”的 API。

## 迁移步骤（建议）

1. 明确唯一默认入口：`Lumen` + SQL-first。
2. APT 保留在 core，但文档明确“可选”。
3. ActiveRecord / Model 下线或改成“高级用法”。
4. 只保留一条主路径示例（SQL-first DAO + 可选 DSL）。
5. 文档给出决策树：SQL-first vs DSL。

## 使用模式

### SQL-first（主路径）

- DAO interface + `@SqlTemplate`。
- APT 可选；运行时解析始终可用。
- `Db.run` 支持临时 SQL。

### DSL（次路径）

- `Dsl` 构建 AST -> renderer -> `Db`。
- 复用同一套 runtime 渲染与绑定模型。
- 说明：DSL 形态已很好（`.select().from().where()`），保留作为“SQL 的结构化表达”。

## 近期交付物

- 最小路径的文档与示例。
- 移除/下沉可选功能。
- 单一启动入口。

## 长期增强方向

- 可选 IDE 插件进行模板检查。
- 可选 APT 编译期诊断。
- 可选代码生成（元模型、DAO registry）。

## 关于“Lumen Client 直接执行字符串 SQL”的思考

你提到的诉求是合理的：
`DefaultDb` + `RenderedSql` 的写法对初学者不友好，心智负担偏高。
但也需要辩证看待：直接执行字符串 SQL 很方便，但可能绕开
绑定与标识符安全边界，容易把“值”和“标识符”混在一起。

### 好处

- 入口更少，`lumen.execute(sql, params...)` 很直观。
- 适合快速落地、示例与实验阶段。

### 风险

- 容易出现字符串拼接造成注入或语义错误。
- 容易弱化模板与 DSL 的价值主张。

### 折中建议（不写死方案，仅供选择）

1) 提供一个极简但安全的 API：
   `lumen.sql("SELECT ... WHERE id = :id").bind("id", 1).list(mapper)`
   仍走模板解析与绑定，避免拼接。

2) 为完全手写 SQL 提供显式入口：
   `lumen.raw("SELECT ...").execute()`
   通过方法名强调“这是不安全路径”。

3) 保留 `Db.run`，但把它包装在 `Lumen` 上，降低心智成本。

建议先做第 3 点（最低成本），再逐步演进到第 1 点（最佳体验）。

## 示例与测试的统一口径

你指出的“示例里只有渲染单测、没有连数据库”是合理的困惑。
现状中既有“渲染/语法单测”也有“DB 集成测试”，两者混在示例里容易让人误解。

建议统一口径：
1) 示例（examples）：至少提供一个“真实 DB 跑通”的用例；
2) 单测（dsl/template）：允许只验证 SQL 输出与绑定；
3) 文档里明确：示例 = 可运行；单测 = 行为验证。

这样可以避免“看起来很奇怪”的感受。

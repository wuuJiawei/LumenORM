# 我又做了一个"美丽小废物"——LumenORM

前几天写 CRUD 接口的时候，第 47 次在 MyBatis XML 里写 `<where>` 标签。

突然就愣住了。

为什么我写 Java 要被迫写 XML？一个简单的查询要拆成接口、XML、配置文件三个地方？说实话，我是在写 ORM，还是在写 XML 培训教材？

然后就有了 LumenORM。

一个业余时间做的、没什么生态的、可能还有很多 bug 的小东西。

但它解决了一个问题：让我不用再写 XML。

---

## 一个简单的查询

假设我们要写一个"根据名称模糊查询用户"的功能。

用 MyBatis，你得先有个 Java 接口。然后再写个 XML 文件，里面要有 select 标签、where 标签、if 标签。字段名要用字符串写，错了编译也不报错，要到运行时才告诉你"空指针"。

如果用了分页，还要配置个 PageInterceptor。调用的时候要先 PageHelper.startPage，完了再 PageInfo。一套下来，一个简单查询涉及四个地方。

我不是说这个方案不好用。它能跑。无数项目都在用。

但我就是不喜欢。

---

## 我的解决方案

LumenORM 的想法很简单：SQL 应该在 Java 里。

不用 XML，不用 mapper 文件，不用运行时才发现拼写错误。

```java
public interface UserRepository extends SqlTemplate {

    @SqlTemplate("""
        SELECT id, name, email, created_at
        FROM users
        WHERE deleted = false
        @if(#{name}) {
            AND name LIKE #{like(#{name})}
        }
        @if(#{status}) {
            AND status = #{status}
        }
        @orderBy(#{sortBy}) {
            ORDER BY #{sortBy} #{sortDir}
        }
        @page(#{page}, #{size}) {
            LIMIT #{offset}, #{size}
        }
        """)
    List<User> search(UserSearchCriteria criteria);
}
```

然后直接注入使用：

```java
@Autowired
UserRepository userRepository;

public List<User> getUsers(String name, String status) {
    var criteria = new UserSearchCriteria(name, status, "created_at", "DESC", 1, 20);
    return userRepository.search(criteria);
}
```

一套东西在一个文件里。结束了。

---

## 动态 SQL

MyBatis 的 `<if>` 标签要学，OGNL 表达式要学，test 属性的语法规则要查文档。

LumenORM 的 @if 就是字面意思：

```java
@SqlTemplate("""
    SELECT * FROM orders WHERE 1=1
    @if(#{userId}) {
        AND user_id = #{userId}
    }
    @if(#{status}) {
        AND status = #{status}
    }
    @if(#{startDate}) {
        AND created_at >= #{startDate}
    }
    @if(#{endDate}) {
        AND created_at <= #{endDate}
    }
    """)
List<Order> findByCondition(OrderQuery query);
```

条件成立，这段 SQL 就加上。条件不成立，这段就消失。

不用百度，不用看文档，英文什么意思它就什么意思。

---

## 内置函数

有些东西 MyBatis 让你自己写，比如 NOW()、UUID()。

LumenORM 内置了一些：

```java
@SqlTemplate("""
    INSERT INTO users (name, email, created_at, update_id, track_id)
    VALUES (#{name}, #{email}, #{now()}, #{currentUserId()}, #{uuid()})
    """)
void insert(User user);
```

`#{now()}` 是当前时间，`#{uuid()}` 是 UUID，`#{like(value)}` 是 LIKE 包裹。都是常用的，不用自己写字符串拼接。

---

## 循环

MyBatis 写 IN 子句要写 `<foreach>`，collection、item、open、separator、close 一堆属性。

LumenORM 用 @for：

```java
@SqlTemplate("""
    SELECT * FROM users WHERE id IN (
        @for((id, index) in #{ids}) {
            #{id}@{if(index < ids.length - 1)}, @end{}
        }
    )
    """)
List<User> findByIds(List<Long> ids);
```

看起来还是有点复杂，但至少语法是统一的，不用学两套东西。

---

## 其他查询方式

如果你不想用接口定义，LumenORM 还提供两种方式。

一种是 Fluent DSL，类型安全的查询构建器：

```java
var t = dsl.table(User.class).as("u");

SelectStmt stmt = dsl.select(
        Dsl.item(t.col(User::getId).expr()),
        Dsl.item(t.col(User::getName).expr()),
        Dsl.item(t.col(User::getEmail).expr())
    )
    .from(t)
    .where(w -> w.and(t.col(User::getStatus).eq("ACTIVE")))
    .orderBy(o -> o.desc(t.col(User::getCreatedAt).expr()))
    .page(1, 20)
    .build();

List<User> users = db.fetch(Query.of(stmt, Bindings.empty()), User.class);
```

字段名写错了？IDE 直接标红。编译阶段就能发现问题。

另一种是文本块模板，直接写原生 SQL：

```java
String sql = """
    SELECT id, name, email
    FROM users
    WHERE status = 'ACTIVE'
    ORDER BY created_at DESC
    """;

List<User> users = db.run(sql, Bindings.empty(), User.class);
```

适合那些"我就是想写 SQL"的场景。

---

## 缺点

说完了优点，说说缺点。

这是我自己业余时间写的。代码我一个人写的，测试我一个人跑的，文档我一个人熬夜写的。Bug 肯定有。我只能说它能用，不敢说它好用。

生态还没起来。没有 MyBatis-Plus 那么多的代码生成器，没有 Spring Data JPA 那么多现成的 Repository。用这个库，你要有自己造轮子的心理准备。

没有 IDE 插件。现在写 @SqlTemplate，参数提示没有，SQL 语法检查没有，列名补全没有。纯靠手打。

---

## 未来

我打算以后做个 IDEA 插件。SQL 语法高亮、参数提示、列名补全、SQL 检查这些。

但这是以后的事。

现在我只能保证：核心功能能用，文档写了，示例跑了。

---

## 什么时候该用它

如果你讨厌 XML，喜欢原生 SQL，想要编译时检查，项目不大愿意接受一个新库，可以试试。

如果你的项目已经用 MyBatis 跑通了，需要很多现成的生态，团队需要市面上有大量经验可借鉴，那还是用 MyBatis 吧。

没必要为了换而换。

---

## 结语

LumenORM 是什么？

一个"美丽小废物"。

它解决了我自己的痛点。它没有庞大的生态，没有商业支持，可能还有很多我没发现的 bug。

但它做到了一件事：让我不用再写 XML。

如果你和我一样，厌倦了 MyBatis + XML + mapper 接口这套组合拳，可以试试。

**仓库地址：https://github.com/wuuJiawei/LumenORM**

最后说一句话：世界上本没有完美方案，只有适合当下需求的方案。如果你正在被 XML 折磨，恭喜你，你找到了一个可能更折磨的方案。但至少，它是 Java 里的折磨。

---

关于作者。

野生 Java 开发者。擅长写"能用但不好看"的代码。信条是：与其花三天配环境，不如花三天写代码。

欢迎提 Issue，欢迎 PR，欢迎吐槽。

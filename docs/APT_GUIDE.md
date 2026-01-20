# APT 与运行时代理（SqlTemplate）使用说明

本页是面向开发与排障的 wiki 文档，说明当前框架中 APT（注解处理器）与运行时代理的角色、边界与取舍。
如果你觉得“又有反射又有 APT”很混乱，可以先看“结论速览”。

---

## 结论速览

- **两条路径不会冲突**：有 APT 生成的 `*_Impl` 就优先用；没有则退回运行时代理。
- **APT 更适合生产**：编译期发现问题、启动更快、无反射解析开销。
- **运行时代理更适合原型/快速迭代**：无需处理器配置，但需要 `-parameters` 保证参数名可用。

---

## APT 做了什么

APT 指的是 `io.lighting.lumen.apt.SqlTemplateProcessor`，它会在**编译期**对 `@SqlTemplate` 和 `@SqlConst` 做处理：

1. **模板语法与绑定校验**
   - 解析模板语法（`@if/@for/@where/@orderBy/@page` 等）。
   - 校验绑定变量是否齐全。
   - 校验 `@orderBy` 允许片段不能含参数。
2. **生成模板常量类**
   - 生成 `*_SqlTemplates` 类，内含模板字符串与已解析的模板对象。
3. **生成 DAO 实现类**
   - 生成 `*_Impl`，将 `@SqlTemplate` 方法编译为可直接执行的 Java 代码。
   - 根据方法签名生成 List / 单行 / PageResult / RenderedSql / Command / Query 等不同执行分支。
   - 在 PageResult 场景中生成分页与 count 逻辑（支持 `PageRequest.withoutCount()`）。
4. **校验 SqlConst**
   - `@SqlConst` 只能标在编译期常量字符串上，且会进行模板语法检查。

---

## 运行时代理做了什么

运行时代理是 `io.lighting.lumen.template.SqlTemplateProxyFactory`：

- 在运行时扫描接口方法上的 `@SqlTemplate`。
- 解析模板并在调用时执行渲染、分页、查询与映射。
- 通过反射获取参数名来做绑定，因此需要编译时加 `-parameters`。

---

## 两条路径如何选择（不会冲突）

创建 DAO 的入口在 `io.lighting.lumen.Lumen#dao`：

1. 先尝试加载 `接口名 + "_Impl"`（APT 生成）。
2. 如果不存在（或类加载失败），则回退到运行时代理。

因此 **不会出现两套实现同时生效的冲突**，只会走一条路径。

---

## 使用体验与性能对比

| 维度 | APT | 运行时代理 |
|---|---|---|
| 错误发现 | 编译期（更早） | 运行时（更晚） |
| 启动成本 | 低 | 需要反射解析 |
| 运行期开销 | 低（直接代码） | 略高（反射 + 动态分派） |
| 接入成本 | 需要配置处理器 | 无需配置 |
| 参数名要求 | 不依赖 `-parameters` | 需要 `-parameters` |

**推荐策略**：
- 生产/大型项目：启用 APT。
- 原型/脚本/临时实验：运行时代理足够。

---

## APT 配置示例（Maven）

在业务模块的 `pom.xml` 中启用注解处理器：

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>io.lighting.lumen</groupId>
        <artifactId>lumen-core</artifactId>
        <version>${project.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

若使用运行时代理，请确保编译开启参数名保留：

```xml
<compilerArgs>
  <arg>-parameters</arg>
</compilerArgs>
```

---

## APT 什么时候会生成实现类

只要接口方法满足以下条件，就会生成 `*_Impl`：

- 方法标注 `@SqlTemplate`
- 方法声明 `throws SQLException`
- 返回类型符合规范（List / PageResult / 单实体 / RenderedSql / Query / Command / int / long / void）

生成结果通常位于：

```
target/generated-sources/annotations/xxx/YourDao_Impl.java
```

---

## 常见疑问（FAQ）

**Q：为什么既有 APT 又有运行时代理？**  
A：APT 是“编译期增强”，运行时代理是“无配置兜底”。两者逻辑一致，但面向不同使用场景。

**Q：会不会造成行为不一致？**  
A：核心逻辑一致，差异主要在“错误暴露时机”和“运行期开销”。若发现行为差异，优先修复 APT 代码生成规则。

**Q：我应该用哪条路径？**  
A：如果项目有固定构建流程、对性能敏感，建议 APT。快速原型/脚手架可先用运行时代理。

---

## 进一步建议

- 为生产模块统一开启 APT，并在 CI 中强制编译检查。
- 对运行时代理入口设置 `-parameters`，避免绑定失败。
- 对复杂 SQL 模板，优先用 APT 规避运行时错误。


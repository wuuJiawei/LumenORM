package io.lighting.lumen.template.annotations;

import io.lighting.lumen.template.EmptyInStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SQL 模板声明注解。
 * <p>
 * 用于在 DAO 接口的方法上声明 SQL 模板，支持以下两种执行路径：
 * <ul>
 *   <li>APT 生成实现类时，将模板编译为可执行逻辑。</li>
 *   <li>运行期动态代理模式下，由运行时解析模板并执行。</li>
 * </ul>
 * 模板语法支持动态节点（如 @if/@for/@where/@page/@orderBy）以及参数绑定（:name），
 * 具体语法由模板解析器定义。
 *
 * <h2>基础用法：原始 SQL（H2/MySQL 等）</h2>
 * <pre>{@code
 * String sql = """
 *     SELECT ID, TITLE, COMPLETED
 *     FROM TODOS
 *     WHERE ID = :id
 *     """;
 *
 * SqlTemplate template = SqlTemplate.parse(sql);
 * RenderedSql rendered = template.render(new TemplateContext(
 *     Map.of("id", 1L),
 *     dialect,
 *     metaRegistry,
 *     entityNameResolver
 * ));
 * }</pre>
 *
 * <h2>类 HQL 风格：表/列名由实体元数据解析</h2>
 * <pre>{@code
 * String hqlLike = """
 *     SELECT
 *       t.@col(TodoEntity::title) AS title,
 *       t.@col(TodoEntity::createdAt) AS createdAt
 *     FROM @table(TodoEntity) t
 *     WHERE t.@col(TodoEntity::completed) = :completed
 *     """;
 * }</pre>
 *
 * <h2>变量绑定与表达式</h2>
 * <ul>
 *   <li><code>:id</code>、<code>:userId</code> —— 简单参数绑定</li>
 *   <li><code>:page.page</code>、<code>:page.pageSize</code> —— 通过路径访问对象字段/Getter</li>
 *   <li><code>:request.title()</code> —— 通过无参方法调用读取值</li>
 * </ul>
 *
 * <h2>内置变量</h2>
 * 使用 <code>::</code> 前缀访问系统绑定，目前内置：
 * <ul>
 *   <li><code>::dialect</code> —— 方言 ID（例如 "h2", "mysql"）</li>
 * </ul>
 * <pre>{@code
 * String sql = """
 *     SELECT * FROM TODOS
 *     @if(::dialect == "h2") { WHERE ID = :id }
 *     """;
 * }</pre>
 *
 * <h2>动态节点</h2>
 * <ul>
 *   <li><code>@if(cond) { ... }</code> —— 条件片段</li>
 *   <li><code>@for(item : items) { ... }</code> —— 循环片段，item 为局部变量</li>
 *   <li><code>@where { ... }</code> / <code>@having { ... }</code> —— 自动处理前置 AND/OR</li>
 *   <li><code>@or { ... }</code> —— 在 where/having 内追加 OR 子句</li>
 *   <li><code>@in(ids)</code> —— 生成 IN 列表，空列表行为由 {@link EmptyInStrategy} 控制</li>
 *   <li><code>@page(page, pageSize)</code> —— 生成方言分页片段</li>
 *   <li><code>@orderBy(selection, allowed={...}, default=...)</code> —— 安全排序白名单</li>
 * </ul>
 *
 * <h3>分页 + 排序示例</h3>
 * <pre>{@code
 * String sql = """
 *     SELECT ID, TITLE
 *     FROM TODOS
 *     @orderBy(:page.sort, allowed={
 *       createdAt: CREATED_AT,
 *       title: TITLE,
 *       createdAtDesc: CREATED_AT DESC
 *     }, default=createdAtDesc)
 *     @page(:page.page, :page.pageSize)
 *     """;
 * }</pre>
 *
 * <h3>列表与 @in 示例</h3>
 * <pre>{@code
 * String sql = """
 *     SELECT ID, TITLE
 *     FROM TODOS
 *     WHERE ID IN @in(ids)
 *     """;
 * }</pre>
 *
 * <h3>@for + @where 示例</h3>
 * <pre>{@code
 * String sql = """
 *     SELECT ID, TITLE
 *     FROM TODOS
 *     @where {
 *       @for(id : ids) { @or { ID = :id } }
 *     }
 *     """;
 * }</pre>
 *
 * @see io.lighting.lumen.template.SqlTemplate
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlTemplate {
    /**
     * SQL 模板文本。
     */
    String value();
}

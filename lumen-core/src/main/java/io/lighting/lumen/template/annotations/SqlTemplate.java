package io.lighting.lumen.template.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SQL 模板声明注解。
 * <p>
 * 用于在 Mapper 接口的方法上声明 SQL 模板，支持以下两种执行路径：
 * <ul>
 *   <li>APT 生成实现类时，将模板编译为可执行逻辑。</li>
 *   <li>运行期动态代理模式下，由运行时解析模板并执行。</li>
 * </ul>
 * 模板语法支持动态节点（如 @if/@for/@where/@page/@orderBy）以及
 * 参数绑定（:name），具体语法由模板解析器定义。
 * <p>
 * 注解保留到运行期，便于框架在运行时读取模板内容进行解析执行。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlTemplate {
    /**
     * SQL 模板文本。
     */
    String value();
}

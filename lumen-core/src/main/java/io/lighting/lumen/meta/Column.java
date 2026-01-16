package io.lighting.lumen.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段到列的映射注解。
 * <p>
 * 用于在实体类字段上显式声明对应的数据库列名。
 * 如果不加该注解，框架可能使用默认命名策略（例如字段名直映射）。
 * <p>
 * 该注解在运行期可见，便于元数据解析与 SQL 渲染时获取列名。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    /**
     * 数据库列名。
     */
    String name();
}

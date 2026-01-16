package io.lighting.lumen.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 主键标识注解。
 * <p>
 * 用于标记实体类中的主键字段，并指定主键生成策略。
 * 该信息会被元数据解析，用于插入、更新、查询等操作的主键处理逻辑。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
    /**
     * 主键生成策略。
     */
    IdStrategy strategy() default IdStrategy.AUTO;
}

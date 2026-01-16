package io.lighting.lumen.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表映射注解。
 * <p>
 * 用于在实体类上声明对应的数据库表名。
 * 若未显式设置，框架可能使用默认命名策略推导表名。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    /**
     * 数据库表名。
     */
    String name();
}

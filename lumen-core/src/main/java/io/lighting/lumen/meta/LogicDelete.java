package io.lighting.lumen.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 逻辑删除标识注解。
 * <p>
 * 标记字段为逻辑删除列，框架在删除操作时会写入 deleted 值，
 * 在查询时可能自动追加过滤条件（排除 deleted 状态）。
 * <p>
 * active/deleted 以字符串形式配置，便于适配不同数据库类型或枚举映射。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LogicDelete {
    /**
     * 未删除（有效）状态值。
     */
    String active() default "0";

    /**
     * 已删除状态值。
     */
    String deleted() default "1";
}

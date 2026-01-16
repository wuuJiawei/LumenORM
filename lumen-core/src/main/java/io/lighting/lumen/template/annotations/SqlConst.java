package io.lighting.lumen.template.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SQL 常量标记注解（仅用于源码级别的提示/分析）。
 * <p>
 * 标注的字符串通常表示 SQL 片段或模板常量，便于在编译期或 IDE
 * 中进行检查、提示或静态分析。该注解只保留到源码阶段，不会
 * 进入字节码，因此不会影响运行期行为。
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD, ElementType.LOCAL_VARIABLE})
public @interface SqlConst {
}

package io.lighting.lumen.dsl;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lambda 入口的列引用解析器。
 * <p>
 * 职责：仅解析方法引用（如 {@code User::getName}）获取属性名。
 * 注意：这是唯一允许反射的地方。
 * <p>
 * 使用示例:
 * <pre>{@code
 * Table table = dsl.table(User.class);
 * table.col(User::getName)  // 解析为 "name"
 * table.col(User::status)   // 解析为 "status"
 * table.col(User::isActive) // 解析为 "active" (boolean)
 * }</pre>
 */
public final class LambdaColumnRef {

    /** 方法引用 → 列名 的缓存 */
    private static final ConcurrentMap<Method, String> CACHE =
        new ConcurrentHashMap<>();

    private LambdaColumnRef() {
    }

    /**
     * 从方法引用解析列名。
     * <p>
     * 解析规则:
     * <ul>
     *   <li>{@code getXxx()} → {@code xxx} (首字母小写)</li>
     *   <li>{@code isXxx()} → {@code xxx} (首字母小写，用于 boolean)</li>
     *   <li>{@code xxx()} → {@code xxx} (直接使用)</li>
     * </ul>
     *
     * @param method 方法引用
     * @return 属性名（列名）
     */
    public static String resolve(Method method) {
        return CACHE.computeIfAbsent(method, LambdaColumnRef::doResolve);
    }

    /**
     * 执行解析（无缓存）。
     */
    private static String doResolve(Method method) {
        String name = method.getName();

        // getXxx() → xxx
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }

        // isXxx() → xxx (boolean 类型)
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }

        // xxx() → xxx
        return name;
    }

    /**
     * 清除缓存（主要用于测试）。
     */
    public static void clearCache() {
        CACHE.clear();
    }
}

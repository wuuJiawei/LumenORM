package io.lighting.lumen.db;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * SQL 执行/渲染日志观察器。
 * <p>
 * 本类作为 {@link DbObserver} 的实现，用于在 SQL 被渲染或执行时输出可读日志。
 * 主要场景：
 * <ul>
 *   <li>排查 SQL 语句生成是否正确（例如 DSL/模板拼接结果）。</li>
 *   <li>观测执行耗时、影响行数等运行时信息。</li>
 *   <li>对参数绑定进行可视化（分离模式或内联模式）。</li>
 * </ul>
 * 配置特点：
 * <ul>
 *   <li>通过 {@link Builder} 进行配置，构建后为不可变对象，线程安全。</li>
 *   <li>支持仅记录渲染、仅记录执行，或两者同时记录。</li>
 *   <li>支持两种输出模式：分离（SQL + binds）或内联（将 ? 替换为格式化值）。</li>
 * </ul>
 * 注意：内联模式仅用于日志展示，不会修改真实执行的 SQL 或参数绑定。
 */
public final class SqlLog implements DbObserver {
    /**
     * 日志输出模式。
     */
    public enum Mode {
        /**
         * SQL 与绑定参数分离输出，例如：SQL | binds=[...]
         */
        SEPARATE,
        /**
         * 以内联方式输出，将 SQL 中的 '?' 按顺序替换为格式化后的绑定值。
         */
        INLINE
    }

    /**
     * 总开关。为 false 时所有日志都不会输出。
     */
    private final boolean enabled;
    /**
     * 是否在 SQL 渲染完成后输出日志。
     */
    private final boolean logOnRender;
    /**
     * 是否在 SQL 执行阶段输出日志。
     */
    private final boolean logOnExecute;
    /**
     * 是否包含耗时信息（纳秒）。
     */
    private final boolean includeElapsed;
    /**
     * 是否包含影响行数信息。
     */
    private final boolean includeRowCount;
    /**
     * 是否包含操作类型（SELECT/INSERT/UPDATE/DELETE 等）。
     */
    private final boolean includeOperation;
    /**
     * 日志输出模式。
     */
    private final Mode mode;
    /**
     * 日志前缀，用于快速识别日志来源。
     */
    private final String prefix;
    /**
     * 日志输出目标，默认 System.out。
     */
    private final Consumer<String> sink;

    private SqlLog(Builder builder) {
        this.enabled = builder.enabled;
        this.logOnRender = builder.logOnRender;
        this.logOnExecute = builder.logOnExecute;
        this.includeElapsed = builder.includeElapsed;
        this.includeRowCount = builder.includeRowCount;
        this.includeOperation = builder.includeOperation;
        this.mode = builder.mode;
        this.prefix = builder.prefix;
        this.sink = builder.sink;
    }

    /**
     * 创建构建器。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * SQL 渲染完成后的回调。
     * <p>
     * 典型用于观察 DSL/模板解析后的 SQL 结果，不涉及执行耗时或行数。
     */
    @Override
    public void afterRender(DbOperation operation, Object source, RenderedSql rendered, long elapsedNanos) {
        if (!enabled || !logOnRender) {
            return;
        }
        sink.accept(format(operation, rendered));
        if (includeElapsed) {
            sink.accept(formatElapsed(operation, elapsedNanos));
        }
    }

    /**
     * SQL 执行前回调。
     * <p>
     * 此处输出的是即将执行的 SQL（若开启内联模式，则会格式化绑定值）。
     */
    @Override
    public void beforeExecute(DbOperation operation, Object source, RenderedSql rendered) {
        if (!enabled || !logOnExecute) {
            return;
        }
        sink.accept(format(operation, rendered));
    }

    /**
     * SQL 执行后回调。
     * <p>
     * 可根据配置输出耗时、影响行数等运行时统计信息。
     */
    @Override
    public void afterExecute(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long elapsedNanos,
        int rowCount
    ) {
        if (!enabled || !logOnExecute) {
            return;
        }
        if (includeRowCount || includeElapsed) {
            sink.accept(formatStats(operation, elapsedNanos, rowCount));
        }
    }

    /**
     * 统一的日志格式入口，决定是否包含操作类型及输出模式。
     */
    private String format(DbOperation operation, RenderedSql rendered) {
        String content = mode == Mode.INLINE ? inlineSql(rendered) : separateSql(rendered);
        if (!includeOperation) {
            return prefix + " " + content;
        }
        return prefix + " [" + operation.name() + "] " + content;
    }

    /**
     * 分离模式：SQL 与绑定参数列表分开输出。
     */
    private String separateSql(RenderedSql rendered) {
        return rendered.sql() + " | binds=" + formatBinds(rendered.binds());
    }

    /**
     * 内联模式：将 SQL 中的 '?' 逐个替换为格式化后的绑定值。
     * <p>
     * 若绑定数量多于占位符数量，会在末尾追加提示。
     */
    private String inlineSql(RenderedSql rendered) {
        List<Bind> binds = rendered.binds();
        if (binds.isEmpty()) {
            return rendered.sql();
        }
        String sql = rendered.sql();
        StringBuilder out = new StringBuilder(sql.length() + binds.size() * 8);
        int bindIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '?' && bindIndex < binds.size()) {
                out.append(formatBind(binds.get(bindIndex++)));
            } else {
                out.append(ch);
            }
        }
        if (bindIndex < binds.size()) {
            out.append(" /* extra binds: ").append(formatExtraBinds(binds, bindIndex)).append(" */");
        }
        return out.toString();
    }

    /**
     * 输出耗时信息（纳秒）。
     */
    private String formatElapsed(DbOperation operation, long elapsedNanos) {
        if (includeOperation) {
            return prefix + " [" + operation.name() + "] elapsed=" + elapsedNanos + "ns";
        }
        return prefix + " elapsed=" + elapsedNanos + "ns";
    }

    /**
     * 输出统计信息（耗时、行数）。
     */
    private String formatStats(DbOperation operation, long elapsedNanos, int rowCount) {
        List<String> parts = new ArrayList<>();
        if (includeRowCount) {
            parts.add("rows=" + rowCount);
        }
        if (includeElapsed) {
            parts.add("elapsed=" + elapsedNanos + "ns");
        }
        String detail = String.join(", ", parts);
        if (includeOperation) {
            return prefix + " [" + operation.name() + "] " + detail;
        }
        return prefix + " " + detail;
    }

    /**
     * 以方括号数组形式输出所有绑定值。
     */
    private String formatBinds(List<Bind> binds) {
        if (binds.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder();
        out.append('[');
        for (int i = 0; i < binds.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(formatBind(binds.get(i)));
        }
        out.append(']');
        return out.toString();
    }

    /**
     * 当绑定值数量多于占位符数量时，用于输出剩余绑定值。
     */
    private String formatExtraBinds(List<Bind> binds, int offset) {
        StringBuilder out = new StringBuilder();
        for (int i = offset; i < binds.size(); i++) {
            if (i > offset) {
                out.append(", ");
            }
            out.append(formatBind(binds.get(i)));
        }
        return out.toString();
    }

    /**
     * 将 Bind 封装类型转换为可读文本。
     */
    private String formatBind(Bind bind) {
        if (bind instanceof Bind.NullValue) {
            return "NULL";
        }
        Object value = ((Bind.Value) bind).value();
        return formatValue(value);
    }

    /**
     * 将常见 Java 类型转换为 SQL 字面量风格的字符串。
     * <p>
     * 例如字符串会加引号并做简单转义，日期会按 toString 输出。
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        if (value instanceof Character character) {
            return "'" + escape(String.valueOf(character)) + "'";
        }
        if (value instanceof CharSequence) {
            return "'" + escape(value.toString()) + "'";
        }
        if (value instanceof Enum<?> enumValue) {
            return "'" + escape(enumValue.name()) + "'";
        }
        if (value instanceof TemporalAccessor || value instanceof java.util.Date) {
            return "'" + escape(value.toString()) + "'";
        }
        if (value instanceof byte[] bytes) {
            return "X'" + toHex(bytes) + "'";
        }
        if (value instanceof Iterable<?> iterable) {
            return formatIterable(iterable);
        }
        if (value.getClass().isArray()) {
            return formatArray(value);
        }
        return "'" + escape(value.toString()) + "'";
    }

    /**
     * 格式化 Iterable 类型的值为 (a, b, c) 形式。
     */
    private String formatIterable(Iterable<?> iterable) {
        StringBuilder out = new StringBuilder();
        out.append('(');
        boolean first = true;
        for (Object item : iterable) {
            if (!first) {
                out.append(", ");
            }
            out.append(formatValue(item));
            first = false;
        }
        out.append(')');
        return out.toString();
    }

    /**
     * 格式化数组类型的值为 (a, b, c) 形式。
     */
    private String formatArray(Object array) {
        StringBuilder out = new StringBuilder();
        out.append('(');
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            Object value = java.lang.reflect.Array.get(array, i);
            out.append(formatValue(value));
        }
        out.append(')');
        return out.toString();
    }

    /**
     * 简单的单引号转义，用于日志展示。
     */
    private String escape(String value) {
        return value.replace("'", "''");
    }

    /**
     * 将二进制数组转换为十六进制字符串。
     */
    private String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format("%02X", value));
        }
        return out.toString();
    }

    public static final class Builder {
        private boolean enabled = true;
        private boolean logOnRender = false;
        private boolean logOnExecute = true;
        private boolean includeElapsed = false;
        private boolean includeRowCount = false;
        private boolean includeOperation = true;
        private Mode mode = Mode.SEPARATE;
        private String prefix = "SQL:";
        private Consumer<String> sink = System.out::println;

        /**
         * 全局开关：关闭后不输出任何日志。
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 是否在渲染阶段输出日志。
         */
        public Builder logOnRender(boolean enabled) {
            this.logOnRender = enabled;
            return this;
        }

        /**
         * 是否在执行阶段输出日志。
         */
        public Builder logOnExecute(boolean enabled) {
            this.logOnExecute = enabled;
            return this;
        }

        /**
         * 是否输出耗时信息（纳秒）。
         */
        public Builder includeElapsed(boolean enabled) {
            this.includeElapsed = enabled;
            return this;
        }

        /**
         * 是否输出影响行数。
         */
        public Builder includeRowCount(boolean enabled) {
            this.includeRowCount = enabled;
            return this;
        }

        /**
         * 是否输出操作类型（例如 SELECT/UPDATE）。
         */
        public Builder includeOperation(boolean enabled) {
            this.includeOperation = enabled;
            return this;
        }

        /**
         * 设置输出模式（分离/内联）。
         */
        public Builder mode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * 设置日志前缀。
         */
        public Builder prefix(String prefix) {
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("prefix must not be blank");
            }
            this.prefix = prefix;
            return this;
        }

        /**
         * 设置日志输出目标，例如绑定到 SLF4J。
         */
        public Builder sink(Consumer<String> sink) {
            this.sink = Objects.requireNonNull(sink, "sink");
            return this;
        }

        /**
         * 构建实例。至少需要开启渲染或执行其中之一。
         */
        public SqlLog build() {
            if (!logOnRender && !logOnExecute) {
                throw new IllegalStateException("At least one of logOnRender/logOnExecute must be enabled");
            }
            return new SqlLog(this);
        }
    }
}

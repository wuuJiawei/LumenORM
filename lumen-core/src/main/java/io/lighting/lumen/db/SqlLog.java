package io.lighting.lumen.db;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class SqlLog implements DbObserver {
    public enum Mode {
        SEPARATE,
        INLINE
    }

    private final boolean enabled;
    private final boolean logOnRender;
    private final boolean logOnExecute;
    private final boolean includeElapsed;
    private final boolean includeRowCount;
    private final boolean includeOperation;
    private final Mode mode;
    private final String prefix;
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

    public static Builder builder() {
        return new Builder();
    }

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

    @Override
    public void beforeExecute(DbOperation operation, Object source, RenderedSql rendered) {
        if (!enabled || !logOnExecute) {
            return;
        }
        sink.accept(format(operation, rendered));
    }

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

    private String format(DbOperation operation, RenderedSql rendered) {
        String content = mode == Mode.INLINE ? inlineSql(rendered) : separateSql(rendered);
        if (!includeOperation) {
            return prefix + " " + content;
        }
        return prefix + " [" + operation.name() + "] " + content;
    }

    private String separateSql(RenderedSql rendered) {
        return rendered.sql() + " | binds=" + formatBinds(rendered.binds());
    }

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

    private String formatElapsed(DbOperation operation, long elapsedNanos) {
        if (includeOperation) {
            return prefix + " [" + operation.name() + "] elapsed=" + elapsedNanos + "ns";
        }
        return prefix + " elapsed=" + elapsedNanos + "ns";
    }

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

    private String formatBind(Bind bind) {
        if (bind instanceof Bind.NullValue) {
            return "NULL";
        }
        Object value = ((Bind.Value) bind).value();
        return formatValue(value);
    }

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

    private String escape(String value) {
        return value.replace("'", "''");
    }

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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder logOnRender(boolean enabled) {
            this.logOnRender = enabled;
            return this;
        }

        public Builder logOnExecute(boolean enabled) {
            this.logOnExecute = enabled;
            return this;
        }

        public Builder includeElapsed(boolean enabled) {
            this.includeElapsed = enabled;
            return this;
        }

        public Builder includeRowCount(boolean enabled) {
            this.includeRowCount = enabled;
            return this;
        }

        public Builder includeOperation(boolean enabled) {
            this.includeOperation = enabled;
            return this;
        }

        public Builder mode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder prefix(String prefix) {
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("prefix must not be blank");
            }
            this.prefix = prefix;
            return this;
        }

        public Builder sink(Consumer<String> sink) {
            this.sink = Objects.requireNonNull(sink, "sink");
            return this;
        }

        public SqlLog build() {
            if (!logOnRender && !logOnExecute) {
                throw new IllegalStateException("At least one of logOnRender/logOnExecute must be enabled");
            }
            return new SqlLog(this);
        }
    }
}

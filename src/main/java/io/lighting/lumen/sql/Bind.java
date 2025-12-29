package io.lighting.lumen.sql;

import java.util.Objects;

public sealed interface Bind permits Bind.Value, Bind.NullValue {
    int jdbcType();

    record Value(Object value, int jdbcType) implements Bind {
        public Value {
            Objects.requireNonNull(value, "value");
        }
    }

    record NullValue(int jdbcType) implements Bind {
    }
}

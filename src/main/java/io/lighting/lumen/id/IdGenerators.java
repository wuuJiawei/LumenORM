package io.lighting.lumen.id;

import io.lighting.lumen.meta.IdStrategy;
import java.util.Objects;
import java.util.UUID;

public final class IdGenerators {
    private static final IdGenerator<UUID> UUID_GENERATOR = UUID::randomUUID;
    private static final IdGenerator<Long> DEFAULT_SNOWFLAKE = SnowflakeIdGenerator.builder().build();
    private static final IdGenerator<Object> AUTO = () -> null;

    private IdGenerators() {
    }

    public static IdGenerator<UUID> uuid() {
        return UUID_GENERATOR;
    }

    public static IdGenerator<Long> snowflake() {
        return DEFAULT_SNOWFLAKE;
    }

    public static IdGenerator<Long> snowflake(long workerId, long datacenterId) {
        return SnowflakeIdGenerator.builder()
            .workerId(workerId)
            .datacenterId(datacenterId)
            .build();
    }

    public static IdGenerator<?> forStrategy(IdStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        return switch (strategy) {
            case AUTO -> AUTO;
            case UUID -> uuid();
            case SNOWFLAKE -> snowflake();
        };
    }
}

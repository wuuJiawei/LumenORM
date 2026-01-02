package io.lighting.lumen.id;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class SnowflakeIdGenerator implements IdGenerator<Long> {
    private static final long DEFAULT_EPOCH = 1704067200000L; // 2024-01-01 UTC
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long epoch;
    private final long workerId;
    private final long datacenterId;
    private final long maxBackwardsMs;
    private final LongSupplier clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    private SnowflakeIdGenerator(Builder builder) {
        this.epoch = builder.epoch;
        this.workerId = builder.workerId;
        this.datacenterId = builder.datacenterId;
        this.maxBackwardsMs = builder.maxBackwardsMs;
        this.clock = builder.clock;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public synchronized Long nextId() {
        long timestamp = clock.getAsLong();
        if (timestamp < epoch) {
            throw new IllegalStateException("Clock moved before epoch");
        }
        if (timestamp < lastTimestamp) {
            long diff = lastTimestamp - timestamp;
            if (diff > maxBackwardsMs) {
                throw new IllegalStateException("Clock moved backwards by " + diff + "ms");
            }
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = lastTimestamp + 1;
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - epoch) << TIMESTAMP_SHIFT)
            | (datacenterId << DATACENTER_ID_SHIFT)
            | (workerId << WORKER_ID_SHIFT)
            | sequence;
    }

    public static final class Builder {
        private long epoch = DEFAULT_EPOCH;
        private long workerId = 0;
        private long datacenterId = 0;
        private long maxBackwardsMs = 5;
        private LongSupplier clock = System::currentTimeMillis;

        public Builder epoch(long epoch) {
            this.epoch = epoch;
            return this;
        }

        public Builder workerId(long workerId) {
            this.workerId = workerId;
            return this;
        }

        public Builder datacenterId(long datacenterId) {
            this.datacenterId = datacenterId;
            return this;
        }

        public Builder maxBackwardsMs(long maxBackwardsMs) {
            this.maxBackwardsMs = maxBackwardsMs;
            return this;
        }

        public Builder clock(LongSupplier clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public SnowflakeIdGenerator build() {
            if (workerId < 0 || workerId > MAX_WORKER_ID) {
                throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
            }
            if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
                throw new IllegalArgumentException("datacenterId must be between 0 and " + MAX_DATACENTER_ID);
            }
            if (epoch < 0) {
                throw new IllegalArgumentException("epoch must be >= 0");
            }
            if (maxBackwardsMs < 0) {
                throw new IllegalArgumentException("maxBackwardsMs must be >= 0");
            }
            return new SnowflakeIdGenerator(this);
        }
    }
}

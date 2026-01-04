package io.lighting.lumen.id;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdGeneratorsTest {
    @Test
    void generatesUuid() {
        UUID first = IdGenerators.uuid().nextId();
        UUID second = IdGenerators.uuid().nextId();

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void snowflakeHandlesClockRollback() {
        TestClock clock = new TestClock(1_000);
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.builder()
            .clock(clock::get)
            .maxBackwardsMs(10)
            .epoch(0)
            .build();

        long first = generator.nextId();
        clock.set(999);
        long second = generator.nextId();

        assertTrue(second > first);
    }

    @Test
    void snowflakeRejectsLargeRollback() {
        TestClock clock = new TestClock(1_000);
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.builder()
            .clock(clock::get)
            .maxBackwardsMs(0)
            .epoch(0)
            .build();

        generator.nextId();
        clock.set(900);

        assertThrows(IllegalStateException.class, generator::nextId);
    }

    @Test
    void strategyMappingResolvesGenerators() {
        assertNull(IdGenerators.forStrategy(io.lighting.lumen.meta.IdStrategy.AUTO).nextId());
        assertNotNull(IdGenerators.forStrategy(io.lighting.lumen.meta.IdStrategy.UUID).nextId());
        assertNotNull(IdGenerators.forStrategy(io.lighting.lumen.meta.IdStrategy.SNOWFLAKE).nextId());
    }

    private static final class TestClock {
        private long value;

        private TestClock(long value) {
            this.value = value;
        }

        long get() {
            return value;
        }

        void set(long value) {
            this.value = value;
        }
    }
}

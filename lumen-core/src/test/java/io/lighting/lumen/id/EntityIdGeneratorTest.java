package io.lighting.lumen.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.TestEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityIdGeneratorTest {
    @Test
    void generatesUuidForStrategy() {
        EntityIdGenerator generator = new EntityIdGenerator(new TestEntityMetaRegistry());

        Optional<Object> id = generator.generate(UuidEntity.class);

        assertTrue(id.isPresent());
        assertTrue(id.get() instanceof UUID);
    }

    @Test
    void returnsEmptyForAutoStrategy() {
        EntityIdGenerator generator = new EntityIdGenerator(new TestEntityMetaRegistry());

        Optional<Object> id = generator.generate(AutoEntity.class);

        assertEquals(Optional.empty(), id);
    }

    @Test
    void missingIdFails() {
        EntityIdGenerator generator = new EntityIdGenerator(new TestEntityMetaRegistry());

        assertThrows(IllegalArgumentException.class, () -> generator.generate(MissingId.class));
    }

    @Table(name = "uuid_entity")
    private static final class UuidEntity {
        @Id(strategy = IdStrategy.UUID)
        private String id;
    }

    @Table(name = "auto_entity")
    private static final class AutoEntity {
        @Id(strategy = IdStrategy.AUTO)
        private long id;
    }

    @Table(name = "missing_id")
    private static final class MissingId {
        private long id;
    }
}

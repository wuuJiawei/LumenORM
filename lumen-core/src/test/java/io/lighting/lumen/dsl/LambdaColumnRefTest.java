package io.lighting.lumen.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.Table;
import org.junit.jupiter.api.Test;

class LambdaColumnRefTest {
    @Test
    void resolvesGetterAndMethodNames() {
        EntityMetaRegistry registry = new TestEntityMetaRegistry();
        Dsl dsl = new Dsl(registry);
        io.lighting.lumen.dsl.Table table = dsl.table(Sample.class);

        assertEquals("status", table.col(Sample::getStatus).expr().columnName());
        assertEquals("status", table.col(Sample::status).expr().columnName());
        assertEquals("active", table.col(Sample::isActive).expr().columnName());
    }

    @Test
    void rejectsMismatchedOwner() {
        EntityMetaRegistry registry = new TestEntityMetaRegistry();
        Dsl dsl = new Dsl(registry);
        io.lighting.lumen.dsl.Table table = dsl.table(Sample.class);

        assertThrows(IllegalArgumentException.class, () -> table.col(Other::getName));
    }

    @Table(name = "samples")
    private static final class Sample {
        @Id
        @Column(name = "id")
        private Long id;

        @Column(name = "status")
        private String status;

        @Column(name = "active")
        private boolean active;

        public Long getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public String status() {
            return status;
        }

        public boolean isActive() {
            return active;
        }
    }

    private static final class Other {
        public String getName() {
            return "x";
        }
    }

    private static class TestEntityMetaRegistry implements EntityMetaRegistry {
        @Override
        public io.lighting.lumen.meta.EntityMeta metaOf(Class<?> entityType) {
            if (entityType == Sample.class) {
                return new io.lighting.lumen.meta.EntityMeta(
                    "samples",
                    java.util.Map.of("id", "id", "status", "status", "active", "active"),
                    new io.lighting.lumen.meta.IdMeta("id", "id", io.lighting.lumen.meta.IdStrategy.AUTO),
                    null
                );
            }
            throw new IllegalArgumentException("Unknown entity: " + entityType);
        }
    }
}

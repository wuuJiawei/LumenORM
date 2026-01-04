package io.lighting.lumen.template;

import io.lighting.lumen.meta.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityNameResolversTest {
    @Test
    void autoResolvesTableAnnotatedEntity() {
        EntityNameResolver resolver = EntityNameResolvers.auto();
        assertEquals(AutoScanEntity.class, resolver.resolve("AutoScanEntity"));
    }

    @Table(name = "auto_scan")
    static class AutoScanEntity {
    }
}

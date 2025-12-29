package io.lighting.lumen.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BindingsTest {
    @Test
    void requireReturnsValues() {
        Bindings bindings = Bindings.of("id", 1, "name", "lumen");
        assertEquals(1, bindings.require("id"));
        assertEquals("lumen", bindings.require("name"));
        assertTrue(bindings.contains("id"));
    }

    @Test
    void requireMissingThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bindings.empty().require("missing"));
    }

    @Test
    void oddPairsThrow() {
        assertThrows(IllegalArgumentException.class, () -> Bindings.of("id", 1, "name"));
    }

    @Test
    void duplicateNamesThrow() {
        assertThrows(IllegalArgumentException.class, () -> Bindings.of("id", 1, "id", 2));
    }

    @Test
    void nonStringKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> Bindings.of("id", 1, 2, "x"));
    }
}

package io.lighting.lumen.template;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SqlTemplateParserTest {

    @Test
    void rejectsInvalidColumnMacro() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.parse("@col(Order::)"));
    }

    @Test
    void rejectsInvalidPageMacro() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.parse("@page(1)"));
    }

    @Test
    void rejectsUnclosedBlocks() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.parse("@if(true){ SELECT 1 "));
    }

    @Test
    void rejectsInvalidForSyntax() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.parse("@for(x y){ SELECT 1 }"));
    }

    @Test
    void rejectsOrderByWithoutAllowedMappings() {
        assertThrows(IllegalArgumentException.class, () -> SqlTemplate.parse("@orderBy(:sort)"));
    }

    @Test
    void rejectsOrderByDefaultMissingInAllowed() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SqlTemplate.parse("@orderBy(:sort, allowed = { ID_ASC : id ASC }, default = CREATED_DESC)")
        );
    }
}

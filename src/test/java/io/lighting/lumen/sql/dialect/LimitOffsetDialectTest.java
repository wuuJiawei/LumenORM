package io.lighting.lumen.sql.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedPagination;
import java.util.List;
import org.junit.jupiter.api.Test;

class LimitOffsetDialectTest {
    @Test
    void quotesIdentifiers() {
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        assertEquals("\"orders\"", dialect.quoteIdent("orders"));
    }

    @Test
    void rejectsBlankQuote() {
        assertThrows(IllegalArgumentException.class, () -> new LimitOffsetDialect(" "));
    }

    @Test
    void rejectsBlankIdentifier() {
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        assertThrows(IllegalArgumentException.class, () -> dialect.quoteIdent(" "));
    }

    @Test
    void rendersPaginationWithBinds() {
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        RenderedPagination pagination = dialect.renderPagination(2, 10, List.of());
        assertEquals(" LIMIT ? OFFSET ? ", pagination.sqlFragment());
        assertEquals(2, pagination.binds().size());
        assertEquals(10, ((Bind.Value) pagination.binds().get(0)).value());
        assertEquals(10, ((Bind.Value) pagination.binds().get(1)).value());
    }

    @Test
    void rejectsInvalidPaging() {
        LimitOffsetDialect dialect = new LimitOffsetDialect("\"");
        assertThrows(IllegalArgumentException.class, () -> dialect.renderPagination(0, 10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> dialect.renderPagination(1, 0, List.of()));
    }
}

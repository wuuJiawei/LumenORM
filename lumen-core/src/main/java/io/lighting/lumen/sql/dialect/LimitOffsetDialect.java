package io.lighting.lumen.sql.dialect;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.ast.OrderItem;
import java.util.List;
import java.util.Objects;

public final class LimitOffsetDialect implements Dialect {
    private final String id;
    private final String quote;

    public LimitOffsetDialect(String quote) {
        this("limit-offset", quote);
    }

    public LimitOffsetDialect(String id, String quote) {
        this.id = Objects.requireNonNull(id, "id");
        this.quote = Objects.requireNonNull(quote, "quote");
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.quote.isBlank()) {
            throw new IllegalArgumentException("quote must not be blank");
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String quoteIdent(String ident) {
        Objects.requireNonNull(ident, "ident");
        if (ident.isBlank()) {
            throw new IllegalArgumentException("ident must not be blank");
        }
        return quote + ident + quote;
    }

    @Override
    public RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        int offset = Math.max(0, (page - 1) * pageSize);
        return new RenderedPagination(
            " LIMIT ? OFFSET ? ",
            List.of(new Bind.Value(pageSize, 0), new Bind.Value(offset, 0))
        );
    }
}

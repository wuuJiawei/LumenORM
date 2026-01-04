package io.lighting.lumen.sql.dialect;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.ast.OrderItem;
import java.util.List;
import java.util.Objects;

public final class OffsetFetchDialect implements Dialect {
    private final String id;
    private final String quoteStart;
    private final String quoteEnd;
    private final boolean requireOrderBy;

    public OffsetFetchDialect(String id, String quoteStart, String quoteEnd, boolean requireOrderBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.quoteStart = Objects.requireNonNull(quoteStart, "quoteStart");
        this.quoteEnd = Objects.requireNonNull(quoteEnd, "quoteEnd");
        this.requireOrderBy = requireOrderBy;
        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.quoteStart.isBlank() || this.quoteEnd.isBlank()) {
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
        return quoteStart + ident + quoteEnd;
    }

    @Override
    public RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        if (requireOrderBy && (orderBy == null || orderBy.isEmpty())) {
            throw new IllegalArgumentException("ORDER BY is required for pagination in " + id);
        }
        int offset = Math.max(0, (page - 1) * pageSize);
        return new RenderedPagination(
            " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ",
            List.of(new Bind.Value(offset, 0), new Bind.Value(pageSize, 0))
        );
    }
}

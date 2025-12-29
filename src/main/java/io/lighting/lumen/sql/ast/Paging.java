package io.lighting.lumen.sql.ast;

public record Paging(int page, int pageSize) {
    public Paging {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
    }
}

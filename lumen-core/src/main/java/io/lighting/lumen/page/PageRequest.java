package io.lighting.lumen.page;

import java.util.Objects;

public record PageRequest(int page, int pageSize, Sort sort) {
    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        if (sort == null) {
            sort = Sort.unsorted();
        }
    }

    public static PageRequest of(int page, int pageSize) {
        return new PageRequest(page, pageSize, Sort.unsorted());
    }

    public static PageRequest of(int page, int pageSize, Sort sort) {
        return new PageRequest(page, pageSize, sort);
    }

    public PageRequest withSort(Sort sort) {
        Objects.requireNonNull(sort, "sort");
        return new PageRequest(page, pageSize, sort);
    }
}

package io.lighting.lumen.page;

import java.util.Objects;

public record PageRequest(int page, int pageSize, Sort sort, boolean searchCount) {
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
        return new PageRequest(page, pageSize, Sort.unsorted(), true);
    }

    public static PageRequest of(int page, int pageSize, Sort sort) {
        return new PageRequest(page, pageSize, sort, true);
    }

    public static PageRequest of(int page, int pageSize, Sort sort, boolean searchCount) {
        return new PageRequest(page, pageSize, sort, searchCount);
    }

    public PageRequest withSort(Sort sort) {
        Objects.requireNonNull(sort, "sort");
        return new PageRequest(page, pageSize, sort, searchCount);
    }

    public PageRequest withSearchCount(boolean searchCount) {
        return new PageRequest(page, pageSize, sort, searchCount);
    }

    public PageRequest withoutCount() {
        return new PageRequest(page, pageSize, sort, false);
    }
}

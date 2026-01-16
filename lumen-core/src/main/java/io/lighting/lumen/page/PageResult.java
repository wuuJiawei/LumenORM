package io.lighting.lumen.page;

import java.util.List;
import java.util.Objects;

/**
 * Simple pagination result container.
 *
 * @param items    current page items
 * @param page     1-based page index
 * @param pageSize page size
 * @param total    total number of rows, or {@link #TOTAL_UNKNOWN} when count is skipped
 * @param <T>      element type
 */
public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public static final long TOTAL_UNKNOWN = -1L;

    public PageResult {
        Objects.requireNonNull(items, "items");
    }
}

package io.lighting.lumen.page;

import io.lighting.lumen.sql.RenderedSql;
import java.util.Objects;

/**
 * Pagination SQL helpers.
 */
public final class PageSql {
    private PageSql() {
    }

    /**
     * Wrap base SQL as a COUNT query while keeping bind parameters intact.
     *
     * @param rendered base rendered SQL
     * @return count query SQL
     */
    public static RenderedSql wrapCount(RenderedSql rendered) {
        Objects.requireNonNull(rendered, "rendered");
        String sql = rendered.sql();
        String trimmed = sql == null ? "" : sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String wrapped = "SELECT COUNT(*) FROM (" + trimmed + ") AS count_src";
        return new RenderedSql(wrapped, rendered.binds());
    }
}

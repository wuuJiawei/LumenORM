package io.lighting.lumen.sql;

import io.lighting.lumen.sql.ast.OrderItem;
import java.util.List;

public interface Dialect {
    String quoteIdent(String ident);

    RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy);
}

package io.lighting.lumen.sql;

import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.Bind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public interface Dialect {
    String id();

    String quoteIdent(String ident);

    RenderedPagination renderPagination(int page, int pageSize, List<OrderItem> orderBy);

    default RenderedSql renderFunction(String name, List<RenderedSql> args) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(args, "args");
        StringBuilder sql = new StringBuilder();
        List<Bind> binds = new ArrayList<>();
        sql.append(name).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            RenderedSql arg = args.get(i);
            sql.append(arg.sql());
            binds.addAll(arg.binds());
        }
        sql.append(')');
        return new RenderedSql(sql.toString(), binds);
    }
}

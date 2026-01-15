package io.lighting.lumen.example.todo.repo;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.template.SqlTemplate;
import io.lighting.lumen.template.TemplateContext;
import org.springframework.stereotype.Repository;

@Repository
public class DefaultTodoQueryDao implements TodoQueryDao {
    private static final SqlTemplate FIND_BY_ID_TEMPLATE = SqlTemplate.parse(TEMPLATE_FIND_BY_ID);
    private static final SqlTemplate LIST_TEMPLATE = SqlTemplate.parse(TEMPLATE_LIST);
    private static final SqlTemplate COUNT_TEMPLATE = SqlTemplate.parse(TEMPLATE_COUNT);

    private final Lumen lumen;

    public DefaultTodoQueryDao(Lumen lumen) {
        this.lumen = lumen;
    }

    @Override
    public RenderedSql findById(long id) {
        return render(FIND_BY_ID_TEMPLATE, Bindings.of("id", id));
    }

    @Override
    public RenderedSql list(Boolean completed, int page, int pageSize) {
        return render(LIST_TEMPLATE, Bindings.of(
            "completed", completed,
            "page", page,
            "pageSize", pageSize
        ));
    }

    @Override
    public RenderedSql count(Boolean completed) {
        return render(COUNT_TEMPLATE, Bindings.of("completed", completed));
    }

    private RenderedSql render(SqlTemplate template, Bindings bindings) {
        TemplateContext context = new TemplateContext(
            bindings.asMap(),
            lumen.dialect(),
            lumen.metaRegistry(),
            lumen.entityNameResolver()
        );
        return template.render(context);
    }
}

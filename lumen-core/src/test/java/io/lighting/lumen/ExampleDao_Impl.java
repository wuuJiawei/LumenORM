package io.lighting.lumen;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.template.EntityNameResolver;
import java.util.Objects;

public final class ExampleDao_Impl implements ExampleDao {
    @SuppressWarnings("unused")
    public ExampleDao_Impl(
        Db db,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(metaRegistry, "metaRegistry");
        Objects.requireNonNull(entityNameResolver, "entityNameResolver");
    }

    @Override
    public int ping() {
        return 1;
    }
}

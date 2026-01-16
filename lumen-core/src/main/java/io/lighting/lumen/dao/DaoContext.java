package io.lighting.lumen.dao;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.id.EntityIdGenerator;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.SqlRenderer;
import java.util.Objects;

/**
 * Execution context for base DAO CRUD operations.
 */
public final class DaoContext {
    private final Db db;
    private final SqlRenderer renderer;
    private final EntityMetaRegistry metaRegistry;
    private final EntityIdGenerator idGenerator;
    private final Dsl dsl;
    private final boolean filterLogicalDelete;

    private DaoContext(
        Db db,
        SqlRenderer renderer,
        EntityMetaRegistry metaRegistry,
        boolean filterLogicalDelete
    ) {
        this.db = Objects.requireNonNull(db, "db");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.idGenerator = new EntityIdGenerator(metaRegistry);
        this.dsl = new Dsl(metaRegistry);
        this.filterLogicalDelete = filterLogicalDelete;
    }

    public static DaoContext of(Db db, SqlRenderer renderer, EntityMetaRegistry metaRegistry) {
        return new DaoContext(db, renderer, metaRegistry, true);
    }

    public static DaoContext of(
        Db db,
        SqlRenderer renderer,
        EntityMetaRegistry metaRegistry,
        boolean filterLogicalDelete
    ) {
        return new DaoContext(db, renderer, metaRegistry, filterLogicalDelete);
    }

    public Db db() {
        return db;
    }

    public SqlRenderer renderer() {
        return renderer;
    }

    public EntityMetaRegistry metaRegistry() {
        return metaRegistry;
    }

    public EntityIdGenerator idGenerator() {
        return idGenerator;
    }

    public Dsl dsl() {
        return dsl;
    }

    public boolean filterLogicalDelete() {
        return filterLogicalDelete;
    }
}

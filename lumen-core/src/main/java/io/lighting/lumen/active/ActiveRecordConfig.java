package io.lighting.lumen.active;

import io.lighting.lumen.db.Db;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.id.EntityIdGenerator;
import io.lighting.lumen.id.IdGeneratorProvider;
import io.lighting.lumen.id.IdGenerators;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.SqlRenderer;
import java.util.Objects;

public final class ActiveRecordConfig {
    private final Db db;
    private final SqlRenderer renderer;
    private final EntityMetaRegistry metaRegistry;
    private final EntityIdGenerator idGenerator;
    private final Dsl dsl;
    private final boolean filterLogicalDelete;

    private ActiveRecordConfig(Builder builder) {
        this.db = Objects.requireNonNull(builder.db, "db");
        this.renderer = Objects.requireNonNull(builder.renderer, "renderer");
        this.metaRegistry = Objects.requireNonNull(builder.metaRegistry, "metaRegistry");
        this.idGenerator = new EntityIdGenerator(metaRegistry, builder.idGeneratorProvider);
        this.dsl = new Dsl(metaRegistry);
        this.filterLogicalDelete = builder.filterLogicalDelete;
    }

    public static Builder builder() {
        return new Builder();
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

    public static final class Builder {
        private Db db;
        private SqlRenderer renderer;
        private EntityMetaRegistry metaRegistry;
        private IdGeneratorProvider idGeneratorProvider = IdGenerators::forStrategy;
        private boolean filterLogicalDelete = true;

        public Builder db(Db db) {
            this.db = db;
            return this;
        }

        public Builder renderer(SqlRenderer renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder metaRegistry(EntityMetaRegistry metaRegistry) {
            this.metaRegistry = metaRegistry;
            return this;
        }

        public Builder idGeneratorProvider(IdGeneratorProvider provider) {
            this.idGeneratorProvider = Objects.requireNonNull(provider, "provider");
            return this;
        }

        public Builder filterLogicalDelete(boolean filterLogicalDelete) {
            this.filterLogicalDelete = filterLogicalDelete;
            return this;
        }

        public ActiveRecordConfig build() {
            return new ActiveRecordConfig(this);
        }
    }
}

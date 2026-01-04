package io.lighting.lumen.id;

import io.lighting.lumen.meta.EntityMeta;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.IdMeta;
import java.util.Objects;
import java.util.Optional;

public final class EntityIdGenerator {
    private final EntityMetaRegistry metaRegistry;
    private final IdGeneratorProvider provider;

    public EntityIdGenerator(EntityMetaRegistry metaRegistry) {
        this(metaRegistry, IdGenerators::forStrategy);
    }

    public EntityIdGenerator(EntityMetaRegistry metaRegistry, IdGeneratorProvider provider) {
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public Optional<Object> generate(Class<?> entityType) {
        EntityMeta meta = metaRegistry.metaOf(entityType);
        IdMeta idMeta = meta.idMeta()
            .orElseThrow(() -> new IllegalArgumentException("Missing @Id on " + entityType.getName()));
        IdGenerator<?> generator = provider.generator(idMeta.strategy());
        if (generator == null) {
            throw new IllegalStateException("No generator configured for " + idMeta.strategy());
        }
        return Optional.ofNullable(generator.nextId());
    }
}

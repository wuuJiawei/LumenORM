package io.lighting.lumen.id;

import io.lighting.lumen.meta.IdStrategy;

@FunctionalInterface
public interface IdGeneratorProvider {
    IdGenerator<?> generator(IdStrategy strategy);
}

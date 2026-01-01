package io.lighting.lumen.dsl;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface PropertyRef<T, R> extends Function<T, R>, Serializable {
}

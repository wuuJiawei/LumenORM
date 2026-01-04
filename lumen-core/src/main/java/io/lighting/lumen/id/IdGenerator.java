package io.lighting.lumen.id;

@FunctionalInterface
public interface IdGenerator<T> {
    T nextId();
}

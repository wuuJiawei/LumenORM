package io.lighting.lumen.template;

@FunctionalInterface
public interface EntityNameResolver {
    Class<?> resolve(String name);
}

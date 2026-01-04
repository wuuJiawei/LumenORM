package io.lighting.lumen.template;

import java.util.Map;
import java.util.Objects;

public final class EntityNameResolvers {
    private EntityNameResolvers() {
    }

    public static EntityNameResolver from(Map<String, Class<?>> mappings) {
        Map<String, Class<?>> resolved = Map.copyOf(mappings);
        return name -> {
            Class<?> type = resolved.get(name);
            if (type != null) {
                return type;
            }
            if (name.contains(".")) {
                try {
                    return Class.forName(name);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalArgumentException("Unknown entity type: " + name, ex);
                }
            }
            throw new IllegalArgumentException("Unknown entity type: " + name);
        };
    }

    public static EntityNameResolver withFallback(EntityNameResolver primary, EntityNameResolver fallback) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(fallback, "fallback");
        return name -> {
            try {
                return primary.resolve(name);
            } catch (IllegalArgumentException ex) {
                return fallback.resolve(name);
            }
        };
    }
}

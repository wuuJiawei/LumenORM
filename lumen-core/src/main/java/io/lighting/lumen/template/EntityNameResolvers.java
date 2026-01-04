package io.lighting.lumen.template;

import java.util.Map;
import java.util.Objects;

public final class EntityNameResolvers {
    private EntityNameResolvers() {
    }

    public static EntityNameResolver auto() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = EntityNameResolvers.class.getClassLoader();
        }
        return auto(classLoader);
    }

    public static EntityNameResolver auto(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        return new AutoEntityNameResolver(classLoader);
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

    private static final class AutoEntityNameResolver implements EntityNameResolver {
        private final ClassLoader classLoader;
        private volatile ClassPathTableScanner.ScanResult scanResult;

        private AutoEntityNameResolver(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public Class<?> resolve(String name) {
            Objects.requireNonNull(name, "name");
            if (name.contains(".")) {
                return loadClass(name);
            }
            ClassPathTableScanner.ScanResult result = scanResult;
            if (result == null) {
                synchronized (this) {
                    if (scanResult == null) {
                        scanResult = ClassPathTableScanner.scan(classLoader);
                    }
                    result = scanResult;
                }
            }
            Class<?> type = result.entities().get(name);
            if (type != null) {
                return type;
            }
            java.util.List<String> candidates = result.conflicts().get(name);
            if (candidates != null && !candidates.isEmpty()) {
                throw new IllegalArgumentException("Ambiguous entity type: " + name + ", candidates: " + candidates);
            }
            throw new IllegalArgumentException("Unknown entity type: " + name);
        }

        private Class<?> loadClass(String name) {
            try {
                return Class.forName(name, false, classLoader);
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Unknown entity type: " + name, ex);
            }
        }
    }
}

package io.lighting.lumen.sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Bindings {
    private final Map<String, Object> values;

    private Bindings(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Bindings empty() {
        return new Bindings(Map.of());
    }

    public static Bindings of(String name, Object value, Object... more) {
        Objects.requireNonNull(name, "name");
        Map<String, Object> entries = new LinkedHashMap<>();
        if (entries.put(name, value) != null) {
            throw new IllegalArgumentException("Duplicate binding: " + name);
        }
        if (more.length % 2 != 0) {
            throw new IllegalArgumentException("Bindings must be name/value pairs");
        }
        for (int i = 0; i < more.length; i += 2) {
            Object key = more[i];
            if (!(key instanceof String keyName)) {
                throw new IllegalArgumentException("Binding name must be a String");
            }
            if (entries.put(keyName, more[i + 1]) != null) {
                throw new IllegalArgumentException("Duplicate binding: " + keyName);
            }
        }
        return new Bindings(entries);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    public Object require(String name) {
        if (!values.containsKey(name)) {
            throw new IllegalArgumentException("Missing binding: " + name);
        }
        return values.get(name);
    }

    public Map<String, Object> asMap() {
        return values;
    }
}

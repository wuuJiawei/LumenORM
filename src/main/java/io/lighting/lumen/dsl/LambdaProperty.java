package io.lighting.lumen.dsl;

import java.beans.Introspector;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class LambdaProperty {
    private static final Map<Class<?>, Property> CACHE = new ConcurrentHashMap<>();

    private LambdaProperty() {
    }

    static Property resolve(PropertyRef<?, ?> ref) {
        Objects.requireNonNull(ref, "ref");
        return CACHE.computeIfAbsent(ref.getClass(), ignored -> extract(ref));
    }

    private static Property extract(PropertyRef<?, ?> ref) {
        SerializedLambda lambda = serialized(ref);
        String ownerName = lambda.getImplClass().replace('/', '.');
        Class<?> owner = loadClass(ownerName);
        String methodName = lambda.getImplMethodName();
        String fieldName = toFieldName(methodName);
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("Unable to resolve property name from " + methodName);
        }
        return new Property(owner, fieldName);
    }

    private static SerializedLambda serialized(Serializable ref) {
        try {
            Method method = ref.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            Object value = method.invoke(ref);
            if (value instanceof SerializedLambda lambda) {
                return lambda;
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Failed to read lambda metadata", ex);
        }
        throw new IllegalArgumentException("Not a lambda");
    }

    private static String toFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Introspector.decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    private static Class<?> loadClass(String name) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Failed to load lambda class " + name, ex);
        }
    }

    record Property(Class<?> owner, String name) {
        Property {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
        }
    }
}

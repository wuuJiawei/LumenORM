package io.lighting.lumen.template;

import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.IdentifierMacros;
import io.lighting.lumen.sql.Dialect;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TemplateContext {
    private final Map<String, Object> values;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;
    private final IdentifierMacros macros;
    private final EntityNameResolver entityNameResolver;
    private final Deque<Map.Entry<String, Object>> locals;

    public TemplateContext(
        Map<String, Object> values,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        this(values, dialect, metaRegistry, entityNameResolver, new ArrayDeque<>());
    }

    private TemplateContext(
        Map<String, Object> values,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver,
        Deque<Map.Entry<String, Object>> locals
    ) {
        this.values = Map.copyOf(values);
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.macros = new IdentifierMacros(this.metaRegistry);
        this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
        this.locals = locals;
    }

    public TemplateContext withLocal(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Deque<Map.Entry<String, Object>> next = new ArrayDeque<>(locals);
        next.push(Map.entry(name, value));
        return new TemplateContext(values, dialect, metaRegistry, entityNameResolver, next);
    }

    public Dialect dialect() {
        return dialect;
    }

    public IdentifierMacros macros() {
        return macros;
    }

    public Class<?> resolveEntity(String name) {
        return entityNameResolver.resolve(name);
    }

    public Object resolvePath(List<PathSegment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        PathSegment first = segments.get(0);
        Object current = resolveName(first.name());
        if (first.methodCall()) {
            current = invokeNoArg(current, first.name());
        }
        for (int i = 1; i < segments.size(); i++) {
            PathSegment segment = segments.get(i);
            if (segment.methodCall()) {
                current = invokeNoArg(current, segment.name());
            } else {
                current = resolveValue(current, segment.name());
            }
        }
        return current;
    }

    private Object resolveValue(Object target, String name) {
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        if (target instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        Method accessor = findAccessor(target.getClass(), name);
        if (accessor != null) {
            try {
                return accessor.invoke(target);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException("Failed to read property: " + name, ex);
            }
        }
        Field field = findField(target.getClass(), name);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalArgumentException("Failed to read field: " + name, ex);
            }
        }
        throw new IllegalArgumentException("Unknown property: " + name + " on " + target.getClass().getName());
    }

    private Method findAccessor(Class<?> type, String name) {
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return type.getMethod("get" + capitalized);
        } catch (NoSuchMethodException ex) {
            try {
                return type.getMethod("is" + capitalized);
            } catch (NoSuchMethodException ignored) {
                if (type.isRecord()) {
                    try {
                        return type.getMethod(name);
                    } catch (NoSuchMethodException secondIgnored) {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    private Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continue
            }
        }
        return null;
    }

    private Object resolveName(String name) {
        for (Map.Entry<String, Object> entry : locals) {
            if (entry.getKey().equals(name)) {
                return entry.getValue();
            }
        }
        if (!values.containsKey(name)) {
            throw new IllegalArgumentException("Missing template binding: " + name);
        }
        return values.get(name);
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findInvokableMethod(target.getClass(), methodName);
            if (method == null) {
                throw new IllegalArgumentException("Unknown method: " + methodName);
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(
                "Failed to invoke method: " + methodName + " on " + target.getClass().getName(),
                ex
            );
        }
    }

    private Method findInvokableMethod(Class<?> type, String methodName) {
        Method direct = findPublicMethod(type, methodName);
        if (direct != null && Modifier.isPublic(direct.getDeclaringClass().getModifiers())) {
            return direct;
        }
        Method interfaceMethod = findPublicInterfaceMethod(type, methodName);
        if (interfaceMethod != null) {
            return interfaceMethod;
        }
        for (Class<?> current = type.getSuperclass(); current != null; current = current.getSuperclass()) {
            Method inherited = findPublicMethod(current, methodName);
            if (inherited != null && Modifier.isPublic(inherited.getDeclaringClass().getModifiers())) {
                return inherited;
            }
            Method inheritedInterface = findPublicInterfaceMethod(current, methodName);
            if (inheritedInterface != null) {
                return inheritedInterface;
            }
        }
        return null;
    }

    private Method findPublicMethod(Class<?> type, String methodName) {
        if (!Modifier.isPublic(type.getModifiers())) {
            return null;
        }
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Method findPublicInterfaceMethod(Class<?> type, String methodName) {
        for (Class<?> iface : type.getInterfaces()) {
            if (Modifier.isPublic(iface.getModifiers())) {
                try {
                    return iface.getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }
            Method nested = findPublicInterfaceMethod(iface, methodName);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

}

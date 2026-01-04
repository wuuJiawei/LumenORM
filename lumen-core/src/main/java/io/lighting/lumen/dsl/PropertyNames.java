package io.lighting.lumen.dsl;

public final class PropertyNames {
    private PropertyNames() {
    }

    public static String name(PropertyRef<?, ?> ref) {
        return LambdaProperty.resolve(ref).name();
    }

    public static Class<?> owner(PropertyRef<?, ?> ref) {
        return LambdaProperty.resolve(ref).owner();
    }
}

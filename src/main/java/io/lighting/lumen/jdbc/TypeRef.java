package io.lighting.lumen.jdbc;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type superType = getClass().getGenericSuperclass();
        if (!(superType instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("TypeRef must be parameterized");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    public Type type() {
        return type;
    }
}

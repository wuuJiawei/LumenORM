package io.lighting.lumen.example.todo.config;

import io.lighting.lumen.Lumen;
import org.springframework.beans.factory.FactoryBean;

public final class LumenDaoFactoryBean<T> implements FactoryBean<T> {
    private final Class<T> daoType;
    private final Lumen lumen;

    public LumenDaoFactoryBean(Class<T> daoType, Lumen lumen) {
        this.daoType = daoType;
        this.lumen = lumen;
    }

    @Override
    public T getObject() {
        return lumen.dao(daoType);
    }

    @Override
    public Class<?> getObjectType() {
        return daoType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}

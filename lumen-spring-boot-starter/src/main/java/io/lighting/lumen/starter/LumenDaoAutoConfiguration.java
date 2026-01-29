package io.lighting.lumen.starter;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.dao.BaseDao;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.template.EntityNameResolver;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;

/**
 * Auto-configuration for LumenORM DAO interfaces.
 * <p>
 * Scans for interfaces extending {@link BaseDao} or containing
 * {@link io.lighting.lumen.template.annotations.SqlTemplate} methods
 * and registers them as Spring beans.
 * <p>
 * The APT-generated *_Impl classes are instantiated via their constructor
 * at runtime - no dynamic proxies or reflection-based invocation.
 */
public class LumenDaoAutoConfiguration {

    /**
     * Configure component scanning for DAO interfaces.
     * <p>
     * Add this to your Spring configuration to enable DAO scanning:
     * <pre>{@code
     * @Configuration
     * @Import(LumenDaoAutoConfiguration.class)
     * public class AppConfig {
     * }
     * }</pre>
     */
    @Configuration
    @ComponentScan(basePackages = {"${lumen.dao.base-packages:}"})
    static class DaoComponentScanConfiguration {
    }

    /**
     * Register DAO interfaces as Spring beans.
     * <p>
     * Uses APT-generated _Impl classes which are instantiated via
     * their constructor - zero reflection at query execution time.
     */
    public static void registerDaoBeans(
        BeanDefinitionRegistry registry,
        Class<?> daoInterface,
        Lumen lumen
    ) {
        String implClassName = daoInterface.getName() + "_Impl";

        try {
            Class<?> implClass = Class.forName(implClassName);
            registerImplBean(registry, daoInterface, implClass, lumen);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "APT-generated implementation not found: " + implClassName
                + ". Ensure annotation processing is enabled.", e
            );
        }
    }

    private static void registerImplBean(
        BeanDefinitionRegistry registry,
        Class<?> daoInterface,
        Class<?> implClass,
        Lumen lumen
    ) {
        String beanName = Character.toLowerCase(daoInterface.getSimpleName().charAt(0))
            + daoInterface.getSimpleName().substring(1);

        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        Constructor<?> ctor = findConstructor(implClass, lumen);
        AbstractBeanDefinition beanDef = BeanDefinitionBuilder
            .genericBeanDefinition(AptDaoFactoryBean.class)
            .addConstructorArgValue(daoInterface)
            .addConstructorArgReference("lumen")
            .getBeanDefinition();

        beanDef.setPrimary(true);
        registry.registerBeanDefinition(beanName, beanDef);
    }

    private static Constructor<?> findConstructor(Class<?> implClass, Lumen lumen) {
        for (Constructor<?> ctor : implClass.getConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 5
                && Db.class.isAssignableFrom(paramTypes[0])
                && Dialect.class.isAssignableFrom(paramTypes[1])
                && EntityMetaRegistry.class.isAssignableFrom(paramTypes[2])
                && EntityNameResolver.class.isAssignableFrom(paramTypes[3])
                && SqlRenderer.class.isAssignableFrom(paramTypes[4])
            ) {
                return ctor;
            }
        }

        for (Constructor<?> ctor : implClass.getConstructors()) {
            if (ctor.getParameterCount() == 4
                && Db.class.isAssignableFrom(ctor.getParameterTypes()[0])
                && Dialect.class.isAssignableFrom(ctor.getParameterTypes()[1])
                && EntityMetaRegistry.class.isAssignableFrom(ctor.getParameterTypes()[2])
                && EntityNameResolver.class.isAssignableFrom(ctor.getParameterTypes()[3])
            ) {
                return ctor;
            }
        }

        throw new IllegalStateException(
            "No suitable constructor found in " + implClass.getName()
        );
    }

    /**
     * Factory bean for APT-generated DAO implementations.
     * <p>
     * Instantiates the APT-generated _Impl class via its constructor.
     * Query execution is direct method call - no reflection involved.
     */
    public static final class AptDaoFactoryBean<T> implements FactoryBean<T> {

        private final Class<T> daoInterface;
        private final String lumenBeanName;
        private T instance;

        public AptDaoFactoryBean(Class<T> daoInterface, String lumenBeanName) {
            this.daoInterface = Objects.requireNonNull(daoInterface, "daoInterface");
            this.lumenBeanName = Objects.requireNonNull(lumenBeanName, "lumenBeanName");
        }

        @Override
        public T getObject() {
            if (instance == null) {
                instance = createInstance();
            }
            return instance;
        }

        @Override
        public Class<?> getObjectType() {
            return daoInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @SuppressWarnings("unchecked")
        private T createInstance() {
            try {
                Lumen lumen = getLumen();
                String implClassName = daoInterface.getName() + "_Impl";
                Class<?> implClass = Class.forName(implClassName);

                Constructor<?> ctor = findCompatibleConstructor(implClass, lumen);
                return (T) ctor.newInstance(
                    lumen.db(), lumen.dialect(), lumen.metaRegistry(),
                    lumen.entityNameResolver(), lumen.renderer()
                );
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to create DAO instance for " + daoInterface.getName(), e
                );
            }
        }

        private Constructor<?> findCompatibleConstructor(Class<?> implClass, Lumen lumen) {
            for (Constructor<?> ctor : implClass.getConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 5
                    && Db.class.isAssignableFrom(types[0])
                    && Dialect.class.isAssignableFrom(types[1])
                    && EntityMetaRegistry.class.isAssignableFrom(types[2])
                    && EntityNameResolver.class.isAssignableFrom(types[3])
                    && SqlRenderer.class.isAssignableFrom(types[4])
                ) {
                    return ctor;
                }
            }

            for (Constructor<?> ctor : implClass.getConstructors()) {
                if (ctor.getParameterCount() == 4
                    && Db.class.isAssignableFrom(ctor.getParameterTypes()[0])
                    && Dialect.class.isAssignableFrom(ctor.getParameterTypes()[1])
                    && EntityMetaRegistry.class.isAssignableFrom(ctor.getParameterTypes()[2])
                    && EntityNameResolver.class.isAssignableFrom(ctor.getParameterTypes()[3])
                ) {
                    return ctor;
                }
            }

            throw new IllegalStateException(
                "No compatible constructor in " + implClass.getName()
            );
        }

        private Lumen getLumen() {
            return Objects.requireNonNull(
                ApplicationContextHolder.getBean(Lumen.class),
                "Lumen bean not found"
            );
        }
    }

    /**
     * Simple holder for ApplicationContext reference.
     * Used to retrieve Spring beans in factory bean.
     */
    private static final class ApplicationContextHolder {
        private static org.springframework.context.ApplicationContext context;

        public static <T> T getBean(Class<T> type) {
            return context != null ? context.getBean(type) : null;
        }

        public static void setContext(org.springframework.context.ApplicationContext ctx) {
            context = ctx;
        }
    }
}

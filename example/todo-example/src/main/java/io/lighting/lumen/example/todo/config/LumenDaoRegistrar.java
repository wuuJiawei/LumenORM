package io.lighting.lumen.example.todo.config;

import io.lighting.lumen.dao.BaseDao;
import io.lighting.lumen.template.annotations.SqlTemplate;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.filter.TypeFilter;

public final class LumenDaoRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private ResourceLoader resourceLoader;
    private Environment environment;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(LumenDaoScan.class.getName());
        String[] basePackages = resolveBasePackages(attributes, importingClassMetadata);
        Set<Class<?>> daoTypes = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            daoTypes.addAll(scanDaoTypes(basePackage));
        }
        for (Class<?> daoType : daoTypes) {
            registerDao(registry, daoType);
        }
    }

    private Set<Class<?>> scanDaoTypes(String basePackage) {
        ClassPathScanningCandidateComponentProvider scanner = environment == null
            ? new LumenDaoScanner()
            : new LumenDaoScanner(environment);
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(interfaceFilter());
        Set<Class<?>> types = new LinkedHashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            String className = candidate.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> type = Class.forName(className, false, loader);
                if (!type.isInterface()) {
                    continue;
                }
                if (BaseDao.class.isAssignableFrom(type) || hasSqlTemplateMethod(type)) {
                    types.add(type);
                }
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Failed to load " + className, ex);
            }
        }
        return types;
    }

    private void registerDao(BeanDefinitionRegistry registry, Class<?> daoType) {
        String beanName = Introspector.decapitalize(daoType.getSimpleName());
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
            .genericBeanDefinition(LumenDaoFactoryBean.class)
            .addConstructorArgValue(daoType)
            .addConstructorArgReference("lumen");
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    private TypeFilter interfaceFilter() {
        return (metadataReader, metadataReaderFactory) -> metadataReader.getClassMetadata().isInterface();
    }

    private boolean hasSqlTemplateMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(SqlTemplate.class)) {
                return true;
            }
        }
        return false;
    }

    private String[] resolveBasePackages(Map<String, Object> attributes, AnnotationMetadata metadata) {
        if (attributes != null) {
            Object value = attributes.get("basePackages");
            if (value instanceof String[] packages && packages.length > 0) {
                return packages;
            }
        }
        String className = metadata.getClassName();
        if (className == null || className.isBlank()) {
            return new String[0];
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return new String[] { "" };
        }
        return new String[] { className.substring(0, lastDot) };
    }

    private static final class LumenDaoScanner extends ClassPathScanningCandidateComponentProvider {
        private LumenDaoScanner() {
            super(false);
        }

        private LumenDaoScanner(Environment environment) {
            super(false, environment);
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition annotated) {
            ClassMetadata metadata = annotated.getMetadata();
            return metadata.isIndependent() && !metadata.isAnnotation() && (metadata.isConcrete() || metadata.isInterface());
        }
    }
}

package org.liteorm.spring.boot;

import org.liteorm.api.SqlEngine;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

final class GeneratedMapperBeanDefinitionRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        String[] mapperPackages = Binder.get(environment)
            .bind("lite-orm.mapper-packages", String[].class)
            .orElse(new String[0]);
        for (String mapperPackage : mapperPackages) {
            registerGeneratedMappers(registry, mapperPackage);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    private void registerGeneratedMappers(BeanDefinitionRegistry registry, String mapperPackage) {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false, environment) {
                @Override
                protected boolean isCandidateComponent(
                        org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                    return beanDefinition.getMetadata().isIndependent()
                        && beanDefinition.getMetadata().isConcrete();
                }
            };
        TypeFilter generatedMapperFilter = (metadataReader, metadataReaderFactory) ->
            metadataReader.getClassMetadata().getClassName().endsWith("MapperImpl");
        scanner.addIncludeFilter(generatedMapperFilter);

        for (BeanDefinition candidate : scanner.findCandidateComponents(mapperPackage)) {
            registerGeneratedMapper(registry, candidate.getBeanClassName());
        }
    }

    private void registerGeneratedMapper(BeanDefinitionRegistry registry, String className) {
        try {
            Class<?> implementationClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            Class<?> mapperInterface = findMapperInterface(implementationClass);
            if (mapperInterface == null || !hasSqlEngineConstructor(implementationClass)) {
                return;
            }

            String beanName = Character.toLowerCase(mapperInterface.getSimpleName().charAt(0))
                + mapperInterface.getSimpleName().substring(1);
            if (registry.containsBeanDefinition(beanName)) {
                return;
            }

            AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(implementationClass)
                .addConstructorArgReference("liteOrmSqlEngine")
                .getBeanDefinition();
            registry.registerBeanDefinition(beanName, beanDefinition);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load generated LiteORM mapper " + className, e);
        }
    }

    private Class<?> findMapperInterface(Class<?> implementationClass) {
        if (!Modifier.isPublic(implementationClass.getModifiers())) {
            return null;
        }
        for (Class<?> implementedInterface : implementationClass.getInterfaces()) {
            if (implementedInterface.isAnnotationPresent(org.liteorm.annotation.Mapper.class)) {
                return implementedInterface;
            }
        }
        return null;
    }

    private boolean hasSqlEngineConstructor(Class<?> implementationClass) {
        for (Constructor<?> constructor : implementationClass.getConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0] == SqlEngine.class) {
                return true;
            }
        }
        return false;
    }
}

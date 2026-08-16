package org.liteorm.spring.boot;

import org.liteorm.api.SqlExecutor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

final class GeneratedMapperBeanDefinitionRegistrar
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        List<LiteOrmProperties.MapperBinding> bindings = Binder.get(environment)
            .bind(
                "lite-orm.mapper-bindings",
                Bindable.listOf(LiteOrmProperties.MapperBinding.class)
            )
            .orElse(List.of());
        validateBindings(registry, bindings);
        for (LiteOrmProperties.MapperBinding binding : bindings) {
            registerGeneratedMappers(registry, binding);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    private void registerGeneratedMappers(
            BeanDefinitionRegistry registry,
            LiteOrmProperties.MapperBinding binding) {
        String executorBeanName = registerExecutor(registry, binding.getDataSource());
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

        for (BeanDefinition candidate : scanner.findCandidateComponents(binding.getPackageName())) {
            registerGeneratedMapper(registry, candidate.getBeanClassName(), binding, executorBeanName);
        }
    }

    private void registerGeneratedMapper(
            BeanDefinitionRegistry registry,
            String className,
            LiteOrmProperties.MapperBinding binding,
            String executorBeanName) {
        try {
            Class<?> implementationClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            Class<?> mapperInterface = findMapperInterface(implementationClass);
            if (mapperInterface == null) {
                throw new BeanDefinitionStoreException(
                    "Invalid generated LiteORM mapper " + className + ": no @Mapper interface is implemented");
            }
            if (!hasSqlExecutorConstructor(implementationClass)) {
                throw new BeanDefinitionStoreException(
                    "Invalid generated LiteORM mapper " + className + ": missing public SqlExecutor constructor");
            }

            String baseName = Character.toLowerCase(mapperInterface.getSimpleName().charAt(0))
                + mapperInterface.getSimpleName().substring(1);
            String beanName = prefixedName(binding.getBeanNamePrefix(), baseName);
            if (registry.containsBeanDefinition(beanName)) {
                BeanDefinition existing = registry.getBeanDefinition(beanName);
                throw new BeanDefinitionStoreException(
                    "Duplicate LiteORM mapper bean '" + beanName + "': " + className
                        + " conflicts with " + existing.getResourceDescription());
            }

            AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(implementationClass)
                .addConstructorArgValue(new RuntimeBeanReference(executorBeanName))
                .getBeanDefinition();
            registry.registerBeanDefinition(beanName, beanDefinition);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load generated LiteORM mapper " + className, e);
        }
    }

    private void validateBindings(
            BeanDefinitionRegistry registry,
            List<LiteOrmProperties.MapperBinding> bindings) {
        for (int index = 0; index < bindings.size(); index++) {
            LiteOrmProperties.MapperBinding binding = bindings.get(index);
            requireText(binding.getPackageName(), "mapper-bindings[" + index + "].package-name");
            requireText(binding.getDataSource(), "mapper-bindings[" + index + "].data-source");
            if (!registry.containsBeanDefinition(binding.getDataSource())) {
                throw new BeanDefinitionStoreException(
                    "No DataSource bean named '" + binding.getDataSource()
                        + "' for Mapper package '" + binding.getPackageName() + "'");
            }
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                String otherPackage = bindings.get(otherIndex).getPackageName();
                if (!binding.getPackageName().equals(otherPackage)
                        && packagesOverlap(binding.getPackageName(), otherPackage)) {
                    throw new BeanDefinitionStoreException(
                        "Mapper package bindings overlap: '" + otherPackage
                            + "' and '" + binding.getPackageName() + "'");
                }
            }
        }
    }

    private String registerExecutor(BeanDefinitionRegistry registry, String dataSourceName) {
        String executorBeanName = "liteOrmSqlExecutor#" + dataSourceName;
        if (!registry.containsBeanDefinition(executorBeanName)) {
            AbstractBeanDefinition executorDefinition = BeanDefinitionBuilder
                .genericBeanDefinition(SpringJdbcSqlExecutorFactoryBean.class)
                .addConstructorArgValue(new RuntimeBeanReference(dataSourceName))
                .getBeanDefinition();
            registry.registerBeanDefinition(executorBeanName, executorDefinition);
        }
        return executorBeanName;
    }

    private boolean packagesOverlap(String left, String right) {
        return left.startsWith(right + ".") || right.startsWith(left + ".");
    }

    private String prefixedName(String prefix, String baseName) {
        if (prefix == null || prefix.isBlank()) {
            return baseName;
        }
        return prefix + Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1);
    }

    private void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new BeanDefinitionStoreException(
                "LiteORM property '" + property + "' must not be blank");
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

    private boolean hasSqlExecutorConstructor(Class<?> implementationClass) {
        for (Constructor<?> constructor : implementationClass.getConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0] == SqlExecutor.class) {
                return true;
            }
        }
        return false;
    }
}

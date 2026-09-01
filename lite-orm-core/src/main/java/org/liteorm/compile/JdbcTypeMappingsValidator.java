package org.liteorm.compile;

import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.JdbcValueAdapter;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates compile-time JDBC mapping declarations before they are selected by a Mapper package.
 */
final class JdbcTypeMappingsValidator {

    private static final String MAPPING_ANNOTATION = "org.liteorm.annotation.JdbcTypeMapping";
    private static final String MAPPING_CONTAINER = "org.liteorm.annotation.JdbcTypeMapping.List";

    private final Elements elements;
    private final Types types;
    private final Messager messager;
    private final TypeMirror mappingsType;
    private final TypeMirror adapterType;

    JdbcTypeMappingsValidator(Elements elements, Types types, Messager messager) {
        this.elements = elements;
        this.types = types;
        this.messager = messager;
        this.mappingsType = elements.getTypeElement(JdbcTypeMappings.class.getCanonicalName()).asType();
        this.adapterType = elements.getTypeElement(JdbcValueAdapter.class.getCanonicalName()).asType();
    }

    void validate(RoundEnvironment roundEnvironment) {
        for (TypeElement mappingsElement : mappingCollections(roundEnvironment)) {
            validateCollection(mappingsElement);
        }
    }

    private Set<TypeElement> mappingCollections(RoundEnvironment roundEnvironment) {
        Set<TypeElement> collections = new LinkedHashSet<>();
        addAnnotatedTypes(roundEnvironment, MAPPING_ANNOTATION, collections);
        addAnnotatedTypes(roundEnvironment, MAPPING_CONTAINER, collections);
        return collections;
    }

    private void addAnnotatedTypes(
            RoundEnvironment roundEnvironment, String annotationName, Set<TypeElement> collections) {
        TypeElement annotation = elements.getTypeElement(annotationName);
        if (annotation == null) {
            return;
        }
        roundEnvironment.getElementsAnnotatedWith(annotation).stream()
            .filter(TypeElement.class::isInstance)
            .map(TypeElement.class::cast)
            .forEach(collections::add);
    }

    private void validateCollection(TypeElement mappingsElement) {
        String mappingsName = mappingsElement.getQualifiedName().toString();
        if (!types.isAssignable(types.erasure(mappingsElement.asType()), types.erasure(mappingsType))) {
            error(mappingsElement, mappingsName + " must implement JdbcTypeMappings");
            return;
        }
        if (!mappingsElement.getModifiers().contains(Modifier.PUBLIC)
                || !mappingsElement.getModifiers().contains(Modifier.FINAL)) {
            error(mappingsElement, mappingsName + " must be a public final JDBC type mappings class");
        }
        if (!hasPublicEnclosingTypes(mappingsElement)) {
            error(mappingsElement, mappingsName
                + " JDBC type mappings class must be enclosed only by public types");
        }
        Set<String> declaredMappings = new LinkedHashSet<>();
        for (AnnotationMirror mapping : mappingAnnotations(mappingsElement)) {
            TypeMirror javaType = typeValue(mapping, "javaType");
            String jdbcType = enumValue(mapping, "jdbcType");
            String mappingKey = javaType + " + " + jdbcType;
            if (!declaredMappings.add(mappingKey)) {
                error(mappingsElement, mapping, "duplicate JDBC type mapping for " + mappingKey);
                continue;
            }
            validateMapping(mappingsElement, mapping);
        }
    }

    private List<AnnotationMirror> mappingAnnotations(TypeElement mappingsElement) {
        List<AnnotationMirror> mappings = new ArrayList<>();
        for (AnnotationMirror annotation : mappingsElement.getAnnotationMirrors()) {
            String annotationName = annotation.getAnnotationType().asElement().toString();
            if (MAPPING_ANNOTATION.equals(annotationName)) {
                mappings.add(annotation);
            } else if (MAPPING_CONTAINER.equals(annotationName)) {
                AnnotationValue value = value(annotation, "value");
                if (value != null && value.getValue() instanceof List<?> repeated) {
                    for (Object item : repeated) {
                        if (item instanceof AnnotationValue annotationValue
                                && annotationValue.getValue() instanceof AnnotationMirror mapping) {
                            mappings.add(mapping);
                        }
                    }
                }
            }
        }
        return mappings;
    }

    private void validateMapping(TypeElement mappingsElement, AnnotationMirror mapping) {
        TypeMirror javaType = typeValue(mapping, "javaType");
        TypeMirror declaredAdapterType = typeValue(mapping, "adapter");
        if (javaType == null || declaredAdapterType == null) {
            error(mappingsElement, mapping, "JDBC type mapping must declare javaType and adapter");
            return;
        }
        TypeElement adapterElement = (TypeElement) types.asElement(declaredAdapterType);
        if (adapterElement == null
                || !types.isAssignable(types.erasure(declaredAdapterType), types.erasure(adapterType))) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " must implement JdbcValueAdapter");
            return;
        }
        if (!adapterElement.getModifiers().contains(Modifier.PUBLIC)
                || adapterElement.getModifiers().contains(Modifier.ABSTRACT)) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " must be a public concrete JDBC value adapter");
            return;
        }
        if (!hasPublicEnclosingTypes(adapterElement)) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " must be enclosed only by public types");
            return;
        }
        if (adapterElement.getNestingKind() == NestingKind.MEMBER
                && !adapterElement.getModifiers().contains(Modifier.STATIC)) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " nested JDBC value adapter must be static");
            return;
        }
        boolean hasPublicNoArgConstructor = adapterElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && constructor.getModifiers().contains(Modifier.PUBLIC));
        if (!hasPublicNoArgConstructor) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " must declare a public no-argument constructor");
            return;
        }
        TypeMirror targetType = adapterTarget(declaredAdapterType);
        if (targetType == null || !isConcreteType(targetType)) {
            error(mappingsElement, mapping, "adapter",
                declaredAdapterType + " must declare a concrete JdbcValueAdapter target type");
            return;
        }
        if (!types.isSameType(types.erasure(javaType), types.erasure(targetType))) {
            error(mappingsElement, mapping, "adapter", "JDBC value adapter target type " + targetType
                + " does not match declared Java type " + javaType);
        }
    }

    private boolean hasPublicEnclosingTypes(TypeElement type) {
        Element enclosing = type.getEnclosingElement();
        while (enclosing instanceof TypeElement enclosingType) {
            if (!enclosingType.getModifiers().contains(Modifier.PUBLIC)) {
                return false;
            }
            enclosing = enclosingType.getEnclosingElement();
        }
        return true;
    }

    private boolean isConcreteType(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return isConcreteType(((ArrayType) type).getComponentType());
        }
        if (type instanceof DeclaredType declaredType) {
            return declaredType.getTypeArguments().stream().allMatch(this::isConcreteType);
        }
        return type.getKind() != TypeKind.TYPEVAR && type.getKind() != TypeKind.WILDCARD;
    }

    private TypeMirror adapterTarget(TypeMirror candidate) {
        if (candidate instanceof DeclaredType declaredType
                && types.isSameType(types.erasure(candidate), types.erasure(adapterType))) {
            return declaredType.getTypeArguments().size() == 1
                ? declaredType.getTypeArguments().getFirst()
                : null;
        }
        for (TypeMirror supertype : types.directSupertypes(candidate)) {
            TypeMirror target = adapterTarget(supertype);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private TypeMirror typeValue(AnnotationMirror annotation, String name) {
        AnnotationValue value = value(annotation, name);
        return value != null && value.getValue() instanceof TypeMirror type ? type : null;
    }

    private String enumValue(AnnotationMirror annotation, String name) {
        AnnotationValue value = value(annotation, name);
        return value != null ? value.getValue().toString() : "<missing>";
    }

    private AnnotationValue value(AnnotationMirror annotation, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : elements.getElementValuesWithDefaults(annotation).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void error(Element element, AnnotationMirror annotation, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
    }

    private void error(Element element, AnnotationMirror annotation, String memberName, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation, value(annotation, memberName));
    }
}

package org.liteorm.compile;

import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.TypeHandler;

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
    private final TypeMirror handlerType;

    JdbcTypeMappingsValidator(Elements elements, Types types, Messager messager) {
        this.elements = elements;
        this.types = types;
        this.messager = messager;
        this.mappingsType = elements.getTypeElement(JdbcTypeMappings.class.getCanonicalName()).asType();
        this.handlerType = elements.getTypeElement(TypeHandler.class.getCanonicalName()).asType();
    }

    void validate(RoundEnvironment roundEnvironment) {
        for (TypeElement mappingsElement : mappingCollections(roundEnvironment)) {
            ValidationResult result = inspect(mappingsElement);
            for (ValidationProblem problem : result.problems()) {
                if (problem.annotation() == null) {
                    messager.printMessage(Diagnostic.Kind.ERROR, problem.message(), mappingsElement);
                } else if (problem.value() == null) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR, problem.message(), mappingsElement, problem.annotation());
                } else {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR, problem.message(), mappingsElement,
                        problem.annotation(), problem.value());
                }
            }
        }
    }

    ValidationResult inspect(TypeElement mappingsElement) {
        List<ValidationProblem> problems = new ArrayList<>();
        List<MappingDeclaration> declarations = new ArrayList<>();
        validateCollection(mappingsElement, declarations, problems);
        return new ValidationResult(List.copyOf(declarations), List.copyOf(problems));
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

    private void validateCollection(
            TypeElement mappingsElement,
            List<MappingDeclaration> declarations,
            List<ValidationProblem> problems) {
        String mappingsName = mappingsElement.getQualifiedName().toString();
        if (!types.isAssignable(types.erasure(mappingsElement.asType()), types.erasure(mappingsType))) {
            problems.add(new ValidationProblem(
                null, null, mappingsName + " must implement JdbcTypeMappings"));
            return;
        }
        if (!mappingsElement.getModifiers().contains(Modifier.PUBLIC)
                || !mappingsElement.getModifiers().contains(Modifier.FINAL)) {
            problems.add(new ValidationProblem(
                null, null, mappingsName + " must be a public final JDBC type mappings class"));
        }
        if (!hasPublicEnclosingTypes(mappingsElement)) {
            problems.add(new ValidationProblem(null, null, mappingsName
                + " JDBC type mappings class must be enclosed only by public types"));
        }
        Set<String> declaredMappings = new LinkedHashSet<>();
        for (AnnotationMirror mapping : mappingAnnotations(mappingsElement)) {
            TypeMirror javaType = typeValue(mapping, "javaType");
            String jdbcType = enumValue(mapping, "jdbcType");
            String vendorTypeName = stringValue(mapping, "vendorTypeName").trim();
            TypeMirror handlerType = typeValue(mapping, "handler");
            declarations.add(new MappingDeclaration(javaType, jdbcType, vendorTypeName, handlerType));
            String mappingKey = javaType + " + " + jdbcType
                + (vendorTypeName.isEmpty() ? "" : " + " + vendorTypeName.toLowerCase(java.util.Locale.ROOT));
            if (!declaredMappings.add(mappingKey)) {
                problems.add(new ValidationProblem(
                    mapping, null, "duplicate JDBC type mapping for " + mappingKey));
                continue;
            }
            validateMapping(mapping, problems);
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

    private void validateMapping(AnnotationMirror mapping, List<ValidationProblem> problems) {
        TypeMirror javaType = typeValue(mapping, "javaType");
        TypeMirror declaredHandlerType = typeValue(mapping, "handler");
        if (javaType == null || declaredHandlerType == null) {
            problems.add(new ValidationProblem(
                mapping, null, "JDBC type mapping must declare javaType and handler"));
            return;
        }
        TypeElement handlerElement = (TypeElement) types.asElement(declaredHandlerType);
        if (handlerElement == null
                || !types.isAssignable(types.erasure(declaredHandlerType), types.erasure(handlerType))) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " must implement TypeHandler"));
            return;
        }
        if (!handlerElement.getModifiers().contains(Modifier.PUBLIC)
                || handlerElement.getModifiers().contains(Modifier.ABSTRACT)) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " must be a public concrete JDBC type handler"));
            return;
        }
        if (!hasPublicEnclosingTypes(handlerElement)) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " must be enclosed only by public types"));
            return;
        }
        if (handlerElement.getNestingKind() == NestingKind.MEMBER
                && !handlerElement.getModifiers().contains(Modifier.STATIC)) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " nested JDBC value type handler must be static"));
            return;
        }
        boolean hasPublicNoArgConstructor = handlerElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && constructor.getModifiers().contains(Modifier.PUBLIC));
        if (!hasPublicNoArgConstructor) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " must declare a public no-argument constructor"));
            return;
        }
        TypeMirror targetType = handlerTarget(declaredHandlerType);
        if (targetType == null || !isConcreteType(targetType)) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                declaredHandlerType + " must declare a concrete TypeHandler target type"));
            return;
        }
        if (!types.isSameType(types.erasure(javaType), types.erasure(targetType))) {
            problems.add(new ValidationProblem(
                mapping, value(mapping, "handler"),
                "JDBC value type handler target type " + targetType
                    + " does not match declared Java type " + javaType));
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

    private TypeMirror handlerTarget(TypeMirror candidate) {
        if (candidate instanceof DeclaredType declaredType
                && types.isSameType(types.erasure(candidate), types.erasure(handlerType))) {
            return declaredType.getTypeArguments().size() == 1
                ? declaredType.getTypeArguments().getFirst()
                : null;
        }
        for (TypeMirror supertype : types.directSupertypes(candidate)) {
            TypeMirror target = handlerTarget(supertype);
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

    private String stringValue(AnnotationMirror annotation, String name) {
        AnnotationValue value = value(annotation, name);
        return value == null ? "" : value.getValue().toString();
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

    record ValidationResult(
        List<MappingDeclaration> declarations,
        List<ValidationProblem> problems
    ) {
    }

    record MappingDeclaration(
        TypeMirror javaType,
        String jdbcType,
        String vendorTypeName,
        TypeMirror handlerType
    ) {
    }

    record ValidationProblem(
        AnnotationMirror annotation,
        AnnotationValue value,
        String message
    ) {
    }
}

package org.liteorm.types.postgresql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSqlTypesPublicApiSurfaceTest {

    private static final Set<String> SUPPORTED_PUBLIC_TYPES = Set.of(
        "org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings",
        "org.liteorm.types.postgresql.PostgreSqlLocalTimeTypeHandler",
        "org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeTypeHandler",
        "org.liteorm.types.postgresql.PostgreSqlOffsetTimeTypeHandler",
        "org.liteorm.types.postgresql.PostgreSqlUuidTypeHandler"
    );
    private static final Set<String> SUPPORTED_PUBLIC_NESTED_TYPES = Set.of(
        "org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings$NationalStringTypeHandler"
    );

    @Test
    void exposesOnlyTheOfficialCollectionAndItsTypeHandlers() throws Exception {
        assertEquals(new TreeSet<>(SUPPORTED_PUBLIC_TYPES), discoverPublicTopLevelTypes());
        assertEquals(
            new TreeSet<>(SUPPORTED_PUBLIC_NESTED_TYPES),
            discoverPublicNestedTypes(PostgreSqlJdbcTypeMappings.class));
    }

    private Set<String> discoverPublicTopLevelTypes() throws IOException {
        Path classesDirectory = Path.of("target", "classes");
        try (var classFiles = Files.walk(classesDirectory.resolve("org/liteorm/types/postgresql"))) {
            return classFiles
                .filter(path -> path.toString().endsWith(".class"))
                .map(classesDirectory::relativize)
                .map(Path::toString)
                .map(path -> path.substring(0, path.length() - ".class".length()))
                .map(path -> path.replace('/', '.').replace('\\', '.'))
                .filter(className -> !className.contains("$"))
                .filter(this::isPublic)
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private boolean isPublic(String className) {
        try {
            return Modifier.isPublic(Class.forName(className, false, getClass().getClassLoader()).getModifiers());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot inspect compiled type " + className, exception);
        }
    }

    private Set<String> discoverPublicNestedTypes(Class<?> owner) {
        return java.util.Arrays.stream(owner.getDeclaredClasses())
            .filter(type -> Modifier.isPublic(type.getModifiers()))
            .map(Class::getName)
            .collect(Collectors.toCollection(TreeSet::new));
    }
}

package io.github.kervix.test.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PublicApiSurfaceTest {

    private static final Set<String> SUPPORTED_PUBLIC_TYPES = Set.of(
        "io.github.kervix.JdbcAssembly",
        "io.github.kervix.Kervix",
        "io.github.kervix.annotation.Batch",
        "io.github.kervix.annotation.Delete",
        "io.github.kervix.annotation.GeneratedKey",
        "io.github.kervix.annotation.Insert",
        "io.github.kervix.annotation.Mapper",
        "io.github.kervix.annotation.Param",
        "io.github.kervix.annotation.Result",
        "io.github.kervix.annotation.Results",
        "io.github.kervix.annotation.Select",
        "io.github.kervix.annotation.Update",
        "io.github.kervix.annotation.UseParameterBinder",
        "io.github.kervix.annotation.UseRowMapper",
        "io.github.kervix.annotation.UseSqlProvider",
        "io.github.kervix.api.BatchExecutionPlan",
        "io.github.kervix.api.BatchDefinition",
        "io.github.kervix.api.BoundParameter",
        "io.github.kervix.api.BoundSql",
        "io.github.kervix.api.BoundSqlBuilder",
        "io.github.kervix.api.BatchResult",
        "io.github.kervix.api.ConfigurationException",
        "io.github.kervix.api.CommandDefinition",
        "io.github.kervix.api.ConnectionHandle",
        "io.github.kervix.api.ConnectionHandleFactory",
        "io.github.kervix.api.CursorCallback",
        "io.github.kervix.api.ExecutionInterceptor",
        "io.github.kervix.api.ExecutionOutcome",
        "io.github.kervix.api.ExecutionPhase",
        "io.github.kervix.api.ExecutionPlan",
        "io.github.kervix.api.GeneratedKeyResult",
        "io.github.kervix.api.JdbcExecutionState",
        "io.github.kervix.api.KervixException",
        "io.github.kervix.api.MappingException",
        "io.github.kervix.api.NonUniqueResultException",
        "io.github.kervix.api.ParameterBinder",
        "io.github.kervix.api.QueryDefinition",
        "io.github.kervix.api.QueryExecutionPlan",
        "io.github.kervix.api.QueryResult",
        "io.github.kervix.api.ResultAssembler",
        "io.github.kervix.api.ResultColumn",
        "io.github.kervix.api.ResultRow",
        "io.github.kervix.api.RowCursor",
        "io.github.kervix.api.RowMapper",
        "io.github.kervix.api.SqlExecutionException",
        "io.github.kervix.api.SqlExecutor",
        "io.github.kervix.api.SqlProvider",
        "io.github.kervix.api.SqlResult",
        "io.github.kervix.api.StatementOptions",
        "io.github.kervix.api.TransactionCallback",
        "io.github.kervix.api.TransactionDomain",
        "io.github.kervix.api.TransactionDomainGuard",
        "io.github.kervix.api.TransactionException",
        "io.github.kervix.api.TransactionalExecutor",
        "io.github.kervix.api.UpdateResult",
        "io.github.kervix.interceptor.AuditExecutionInterceptor",
        "io.github.kervix.interceptor.LoggingExecutionInterceptor",
        "io.github.kervix.interceptor.SlowQueryExecutionInterceptor",
        "io.github.kervix.jdbc.JdbcSqlExecutor",
        "io.github.kervix.jdbc.TypeHandlerManager",
        "io.github.kervix.runtime.ResultValueConverters",
        "io.github.kervix.transaction.SimpleConnectionHandleFactory",
        "io.github.kervix.transaction.SimpleTransactionDomainGuard",
        "io.github.kervix.transaction.SimpleTransactionalExecutor"
    );
    @Test
    void exposesOnlySupportedTopLevelTypes() throws Exception {
        assertEquals(new TreeSet<>(SUPPORTED_PUBLIC_TYPES), discoverPublicTopLevelTypes());
    }

    @Test
    void keepsTypeHandlerManagerResultRoutingInternal() throws Exception {
        Class<?> resultHandler = Class.forName("io.github.kervix.jdbc.TypeHandlerManager$ResultHandler");

        assertFalse(Modifier.isPublic(resultHandler.getModifiers()));
    }

    @Test
    void keepsApiClassesIndependentFromJdbcImplementations() throws Exception {
        Path apiClasses = Path.of("target", "classes", "org", "kervix", "api");
        try (var classFiles = Files.walk(apiClasses)) {
            for (Path classFile : classFiles.filter(path -> path.toString().endsWith(".class")).toList()) {
                String constantPool = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
                assertFalse(constantPool.contains("org/kervix/jdbc"),
                    () -> classFile + " references io.github.kervix.jdbc");
            }
        }
    }

    @Test
    void keepsCompilerImplementationOutOfTheRuntimeArtifact() {
        Path compilerPackage = Path.of("target", "classes", "org", "kervix", "compile");
        Path freemarkerClasses = Path.of("target", "classes", "freemarker");

        assertFalse(Files.exists(compilerPackage));
        assertFalse(Files.exists(freemarkerClasses));
    }

    private Set<String> discoverPublicTopLevelTypes() throws IOException {
        Path classesDirectory = Path.of("target", "classes");
        try (var classFiles = Files.walk(classesDirectory.resolve("org/kervix"))) {
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

}

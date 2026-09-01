package org.liteorm.test.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicApiSurfaceTest {

    private static final Set<String> SUPPORTED_PUBLIC_TYPES = Set.of(
        "org.liteorm.JdbcAssembly",
        "org.liteorm.LiteOrm",
        "org.liteorm.annotation.Batch",
        "org.liteorm.annotation.Column",
        "org.liteorm.annotation.Delete",
        "org.liteorm.annotation.GeneratedKey",
        "org.liteorm.annotation.Insert",
        "org.liteorm.annotation.JdbcTypeMapping",
        "org.liteorm.annotation.Mapper",
        "org.liteorm.annotation.Param",
        "org.liteorm.annotation.Select",
        "org.liteorm.annotation.Update",
        "org.liteorm.annotation.UseParameterBinder",
        "org.liteorm.annotation.UseJdbcTypeMappings",
        "org.liteorm.annotation.UseRowMapper",
        "org.liteorm.annotation.UseSqlProvider",
        "org.liteorm.api.BatchExecutionPlan",
        "org.liteorm.api.BoundParameter",
        "org.liteorm.api.BoundSql",
        "org.liteorm.api.ConfigurationException",
        "org.liteorm.api.ConnectionHandle",
        "org.liteorm.api.ConnectionHandleFactory",
        "org.liteorm.api.CursorCallback",
        "org.liteorm.api.ExecutionInterceptor",
        "org.liteorm.api.ExecutionOutcome",
        "org.liteorm.api.ExecutionPhase",
        "org.liteorm.api.ExecutionPlan",
        "org.liteorm.api.JdbcExecutionState",
        "org.liteorm.api.JdbcTypeMappings",
        "org.liteorm.api.JdbcTypeMappingsMetadata",
        "org.liteorm.api.JdbcValueAdapter",
        "org.liteorm.api.LiteOrmException",
        "org.liteorm.api.MappingException",
        "org.liteorm.api.NonUniqueResultException",
        "org.liteorm.api.ParameterBinder",
        "org.liteorm.api.ResultColumn",
        "org.liteorm.api.RowCursor",
        "org.liteorm.api.RowMapper",
        "org.liteorm.api.SqlExecutionException",
        "org.liteorm.api.SqlExecutor",
        "org.liteorm.api.SqlProvider",
        "org.liteorm.api.SqlResult",
        "org.liteorm.api.StatementOptions",
        "org.liteorm.api.TransactionCallback",
        "org.liteorm.api.TransactionDomain",
        "org.liteorm.api.TransactionDomainGuard",
        "org.liteorm.api.TransactionException",
        "org.liteorm.api.TransactionalExecutor",
        "org.liteorm.compile.LiteOrmProcessor",
        "org.liteorm.interceptor.AuditExecutionInterceptor",
        "org.liteorm.interceptor.LoggingExecutionInterceptor",
        "org.liteorm.interceptor.SlowQueryExecutionInterceptor",
        "org.liteorm.jdbc.JdbcSqlExecutor",
        "org.liteorm.runtime.ResultValueConverters",
        "org.liteorm.transaction.SimpleConnectionHandleFactory",
        "org.liteorm.transaction.SimpleTransactionDomainGuard",
        "org.liteorm.transaction.SimpleTransactionalExecutor"
    );

    @Test
    void exposesOnlySupportedTopLevelTypes() throws Exception {
        assertEquals(new TreeSet<>(SUPPORTED_PUBLIC_TYPES), discoverPublicTopLevelTypes());
    }

    private Set<String> discoverPublicTopLevelTypes() throws IOException {
        Path classesDirectory = Path.of("target", "classes");
        try (var classFiles = Files.walk(classesDirectory.resolve("org/liteorm"))) {
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

package org.liteorm.test.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.compile.LiteOrmProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTypeMappingsCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAConcreteTypeHandlerWhoseValueTypeMatchesTheDeclaration() throws Exception {
        Compilation result = compile("ValidMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(
                javaType = UUID.class,
                jdbcType = JDBCType.OTHER,
                handler = ValidMappings.UuidTypeHandler.class
            )
            public final class ValidMappings implements JdbcTypeMappings {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                            throws SQLException {
                        statement.setObject(index, value);
                    }
                    public UUID getResult(ResultSet resultSet, int columnIndex) throws SQLException {
                        return resultSet.getObject(columnIndex, UUID.class);
                    }
                }
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
    }

    @Test
    void rejectsTypeHandlerWhoseValueTypeDoesNotMatchTheDeclaredJavaType() throws Exception {
        Compilation result = compile("MismatchedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(
                javaType = MismatchedMappings.Money.class,
                jdbcType = JDBCType.DECIMAL,
                handler = MismatchedMappings.StringTypeHandler.class
            )
            public final class MismatchedMappings implements JdbcTypeMappings {
                public record Money(long minorUnits) {}

                public static final class StringTypeHandler implements TypeHandler<String> {
                    public StringTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, String value, JDBCType jdbcType)
                            throws SQLException {}
                    public String getResult(ResultSet resultSet, int columnIndex) throws SQLException {
                        return resultSet.getString(columnIndex);
                    }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "JDBC value type handler target type java.lang.String does not match declared Java type"),
            result::diagnosticsText);
    }

    @Test
    void rejectsDuplicateJavaAndJdbcTypeDeclarations() throws Exception {
        Compilation result = compile("DuplicateMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = DuplicateMappings.FirstTypeHandler.class)
            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = DuplicateMappings.SecondTypeHandler.class)
            public final class DuplicateMappings implements JdbcTypeMappings {
                public static final class FirstTypeHandler implements TypeHandler<UUID> {
                    public FirstTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
                public static final class SecondTypeHandler implements TypeHandler<UUID> {
                    public SecondTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "duplicate JDBC type mapping for java.util.UUID + OTHER"), result::diagnosticsText);
    }

    @Test
    void rejectsTypeHandlerWithoutAPublicNoArgumentConstructor() throws Exception {
        Compilation result = compile("ConstructorMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = ConstructorMappings.UuidTypeHandler.class)
            public final class ConstructorMappings implements JdbcTypeMappings {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    private UuidTypeHandler(String configuration) {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must declare a public no-argument constructor"), result::diagnosticsText);
    }

    @Test
    void rejectsNonStaticNestedTypeHandler() throws Exception {
        Compilation result = compile("NestedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = NestedMappings.UuidTypeHandler.class)
            public final class NestedMappings implements JdbcTypeMappings {
                public final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "nested JDBC value type handler must be static"), result::diagnosticsText);
    }

    @Test
    void rejectsTypeHandlerEnclosedByANonPublicType() throws Exception {
        Compilation result = compile("InaccessibleTypeHandlerMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = TypeHandlerContainer.UuidTypeHandler.class)
            public final class InaccessibleTypeHandlerMappings implements JdbcTypeMappings {}

            final class TypeHandlerContainer {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be enclosed only by public types"), result::diagnosticsText);
    }

    @Test
    void rejectsTypeHandlerWithANonConcreteValueType() throws Exception {
        Compilation result = compile("GenericTypeHandlerMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = GenericTypeHandlerMappings.RawTypeHandler.class)
            public final class GenericTypeHandlerMappings implements JdbcTypeMappings {
                @SuppressWarnings("rawtypes")
                public static final class RawTypeHandler implements TypeHandler {
                    public RawTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, Object value, JDBCType jdbcType) {}
                    public Object getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
    }

    @Test
    void rejectsMappingsClassThatIsNotFinal() throws Exception {
        Compilation result = compile("MutableMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = MutableMappings.UuidTypeHandler.class)
            public class MutableMappings implements JdbcTypeMappings {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public final JDBC type mappings class"), result::diagnosticsText);
    }

    @Test
    void rejectsPackagePrivateMappingsClass() throws Exception {
        Compilation result = compile("PackagePrivateMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = PackagePrivateMappings.UuidTypeHandler.class)
            final class PackagePrivateMappings implements JdbcTypeMappings {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public final JDBC type mappings class"), result::diagnosticsText);
    }

    @Test
    void rejectsMappingsClassEnclosedByANonPublicType() throws Exception {
        Compilation result = compile("EnclosedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            final class EnclosedMappings {
                @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                    handler = Mappings.UuidTypeHandler.class)
                public static final class Mappings implements JdbcTypeMappings {
                    public static final class UuidTypeHandler implements TypeHandler<UUID> {
                        public UuidTypeHandler() {}
                        public void setNonNull(
                                PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                        public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                    }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "JDBC type mappings class must be enclosed only by public types"), result::diagnosticsText);
    }

    @Test
    void rejectsNonPublicTypeHandler() throws Exception {
        Compilation result = compile("NonPublicTypeHandlerMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = NonPublicTypeHandlerMappings.UuidTypeHandler.class)
            public final class NonPublicTypeHandlerMappings implements JdbcTypeMappings {
                static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public concrete JDBC type handler"), result::diagnosticsText);
    }

    @Test
    void rejectsAbstractTypeHandler() throws Exception {
        Compilation result = compile("AbstractTypeHandlerMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = AbstractTypeHandlerMappings.UuidTypeHandler.class)
            public final class AbstractTypeHandlerMappings implements JdbcTypeMappings {
                public abstract static class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public concrete JDBC type handler"), result::diagnosticsText);
    }

    @Test
    void rejectsClassThatDoesNotImplementTypeHandler() throws Exception {
        Compilation result = compile("UnrelatedTypeHandlerMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = String.class)
            public final class UnrelatedTypeHandlerMappings implements JdbcTypeMappings {}
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
    }

    @Test
    void rejectsMappingDeclarationOutsideAJdbcTypeMappingsClass() throws Exception {
        Compilation result = compile("UnmarkedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.TypeHandler;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                handler = UnmarkedMappings.UuidTypeHandler.class)
            public final class UnmarkedMappings {
                public static final class UuidTypeHandler implements TypeHandler<UUID> {
                    public UuidTypeHandler() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must implement JdbcTypeMappings"), result::diagnosticsText);
    }

    private Compilation compile(String typeName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve(typeName).resolve("sources");
        Path classes = temporaryDirectory.resolve(typeName).resolve("classes");
        Path generated = temporaryDirectory.resolve(typeName).resolve("generated");
        Path sourceFile = sources.resolve("org/liteorm/test/mappingfixture/" + typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(List.of(sourceFile));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }
        return new Compilation(succeeded, diagnostics.getDiagnostics());
    }

    private record Compilation(
            boolean succeeded,
            List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}

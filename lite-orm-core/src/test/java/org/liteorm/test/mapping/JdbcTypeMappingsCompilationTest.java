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
    void acceptsAConcreteAdapterWhoseValueTypeMatchesTheDeclaration() throws Exception {
        Compilation result = compile("ValidMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(
                javaType = UUID.class,
                jdbcType = JDBCType.OTHER,
                adapter = ValidMappings.UuidAdapter.class
            )
            public final class ValidMappings implements JdbcTypeMappings {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                            throws SQLException {
                        statement.setObject(index, value);
                    }
                    public UUID getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
                        return resultSet.getObject(columnIndex, UUID.class);
                    }
                }
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
    }

    @Test
    void rejectsAdapterWhoseValueTypeDoesNotMatchTheDeclaredJavaType() throws Exception {
        Compilation result = compile("MismatchedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.sql.SQLException;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(
                javaType = MismatchedMappings.Money.class,
                jdbcType = JDBCType.DECIMAL,
                adapter = MismatchedMappings.StringAdapter.class
            )
            public final class MismatchedMappings implements JdbcTypeMappings {
                public record Money(long minorUnits) {}

                public static final class StringAdapter implements JdbcValueAdapter<String> {
                    public StringAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, String value, JDBCType jdbcType)
                            throws SQLException {}
                    public String getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
                        return resultSet.getString(columnIndex);
                    }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "JDBC value adapter target type java.lang.String does not match declared Java type"),
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
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = DuplicateMappings.FirstAdapter.class)
            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = DuplicateMappings.SecondAdapter.class)
            public final class DuplicateMappings implements JdbcTypeMappings {
                public static final class FirstAdapter implements JdbcValueAdapter<UUID> {
                    public FirstAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
                public static final class SecondAdapter implements JdbcValueAdapter<UUID> {
                    public SecondAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "duplicate JDBC type mapping for java.util.UUID + OTHER"), result::diagnosticsText);
    }

    @Test
    void rejectsAdapterWithoutAPublicNoArgumentConstructor() throws Exception {
        Compilation result = compile("ConstructorMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = ConstructorMappings.UuidAdapter.class)
            public final class ConstructorMappings implements JdbcTypeMappings {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    private UuidAdapter(String configuration) {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must declare a public no-argument constructor"), result::diagnosticsText);
    }

    @Test
    void rejectsNonStaticNestedAdapter() throws Exception {
        Compilation result = compile("NestedMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = NestedMappings.UuidAdapter.class)
            public final class NestedMappings implements JdbcTypeMappings {
                public final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "nested JDBC value adapter must be static"), result::diagnosticsText);
    }

    @Test
    void rejectsAdapterEnclosedByANonPublicType() throws Exception {
        Compilation result = compile("InaccessibleAdapterMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = AdapterContainer.UuidAdapter.class)
            public final class InaccessibleAdapterMappings implements JdbcTypeMappings {}

            final class AdapterContainer {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be enclosed only by public types"), result::diagnosticsText);
    }

    @Test
    void rejectsAdapterWithANonConcreteValueType() throws Exception {
        Compilation result = compile("GenericAdapterMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = GenericAdapterMappings.RawAdapter.class)
            public final class GenericAdapterMappings implements JdbcTypeMappings {
                @SuppressWarnings("rawtypes")
                public static final class RawAdapter implements JdbcValueAdapter {
                    public RawAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, Object value, JDBCType jdbcType) {}
                    public Object getNullable(ResultSet resultSet, int columnIndex) { return null; }
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
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = MutableMappings.UuidAdapter.class)
            public class MutableMappings implements JdbcTypeMappings {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
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
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = PackagePrivateMappings.UuidAdapter.class)
            final class PackagePrivateMappings implements JdbcTypeMappings {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
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
            import org.liteorm.api.JdbcValueAdapter;

            final class EnclosedMappings {
                @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                    adapter = Mappings.UuidAdapter.class)
                public static final class Mappings implements JdbcTypeMappings {
                    public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                        public UuidAdapter() {}
                        public void setNonNull(
                                PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                        public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                    }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "JDBC type mappings class must be enclosed only by public types"), result::diagnosticsText);
    }

    @Test
    void rejectsNonPublicAdapter() throws Exception {
        Compilation result = compile("NonPublicAdapterMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = NonPublicAdapterMappings.UuidAdapter.class)
            public final class NonPublicAdapterMappings implements JdbcTypeMappings {
                static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public concrete JDBC value adapter"), result::diagnosticsText);
    }

    @Test
    void rejectsAbstractAdapter() throws Exception {
        Compilation result = compile("AbstractAdapterMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.sql.PreparedStatement;
            import java.sql.ResultSet;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = AbstractAdapterMappings.UuidAdapter.class)
            public final class AbstractAdapterMappings implements JdbcTypeMappings {
                public abstract static class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
                }
            }
            """);

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "must be a public concrete JDBC value adapter"), result::diagnosticsText);
    }

    @Test
    void rejectsClassThatDoesNotImplementJdbcValueAdapter() throws Exception {
        Compilation result = compile("UnrelatedAdapterMappings", """
            package org.liteorm.test.mappingfixture;

            import java.sql.JDBCType;
            import java.util.UUID;
            import org.liteorm.annotation.JdbcTypeMapping;
            import org.liteorm.api.JdbcTypeMappings;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = String.class)
            public final class UnrelatedAdapterMappings implements JdbcTypeMappings {}
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
            import org.liteorm.api.JdbcValueAdapter;

            @JdbcTypeMapping(javaType = UUID.class, jdbcType = JDBCType.OTHER,
                adapter = UnmarkedMappings.UuidAdapter.class)
            public final class UnmarkedMappings {
                public static final class UuidAdapter implements JdbcValueAdapter<UUID> {
                    public UuidAdapter() {}
                    public void setNonNull(PreparedStatement statement, int index, UUID value, JDBCType jdbcType) {}
                    public UUID getNullable(ResultSet resultSet, int columnIndex) { return null; }
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

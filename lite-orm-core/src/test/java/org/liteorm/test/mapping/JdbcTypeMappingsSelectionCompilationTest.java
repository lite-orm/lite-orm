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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTypeMappingsSelectionCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsMapperPackageWithoutJdbcTypeMappingsSelection() throws Exception {
        Compilation result = compile(new SourceFile(
            "org/liteorm/test/selection/missing/MissingSelectionMapper.java",
            """
                package org.liteorm.test.selection.missing;

                import org.liteorm.annotation.Mapper;
                import org.liteorm.annotation.Select;

                @Mapper
                interface MissingSelectionMapper {
                    @Select("SELECT name FROM users")
                    String findName();
                }
                """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Mapper package org.liteorm.test.selection.missing must select exactly one JdbcTypeMappings collection"),
            result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.missing.MissingSelectionMapper"), result::diagnosticsText);
    }

    @Test
    void rejectsMalformedSelectedJdbcTypeMappingsWithPackageAndMapperContext() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/malformed/MalformedMappings.java",
                """
                    package org.liteorm.test.selection.malformed;

                    import org.liteorm.api.JdbcTypeMappings;

                    public class MalformedMappings implements JdbcTypeMappings {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/malformed/package-info.java",
                """
                    @UseJdbcTypeMappings(MalformedMappings.class)
                    package org.liteorm.test.selection.malformed;

                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/malformed/MalformedSelectionMapper.java",
                """
                    package org.liteorm.test.selection.malformed;

                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    interface MalformedSelectionMapper {
                        @Select("SELECT name FROM users")
                        String findName();
                    }
                    """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.malformed.MalformedMappings must be a public final JDBC type mappings class"),
            result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Mapper package org.liteorm.test.selection.malformed"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.malformed.MalformedSelectionMapper"), result::diagnosticsText);
    }

    @Test
    void preservesSelectedCollectionIdentityWithoutChangingMapperConstruction() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/metadata/MetadataMappings.java",
                """
                    package org.liteorm.test.selection.metadata;

                    import org.liteorm.api.JdbcTypeMappings;

                    public final class MetadataMappings implements JdbcTypeMappings {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/metadata/package-info.java",
                """
                    @UseJdbcTypeMappings(MetadataMappings.class)
                    package org.liteorm.test.selection.metadata;

                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/metadata/MetadataMapper.java",
                """
                    package org.liteorm.test.selection.metadata;

                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface MetadataMapper {
                        @Select("SELECT name FROM users")
                        String findName();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/metadata/MetadataMapperImpl.java"));
        assertTrue(generated.contains(
            "implements MetadataMapper, JdbcTypeMappingsMetadata"), generated);
        assertTrue(generated.contains(
            "public Class<? extends JdbcTypeMappings> jdbcTypeMappings()"), generated);
        assertTrue(generated.contains("return MetadataMappings.class;"), generated);
        assertEquals(1, countOccurrences(generated, "public MetadataMapperImpl("), generated);
        assertTrue(generated.contains("public MetadataMapperImpl(SqlExecutor sqlExecutor)"), generated);
    }

    @Test
    void generatesDirectNullSafeParameterBindingThroughSelectedAdapter() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/binding/Money.java",
                """
                    package org.liteorm.test.selection.binding;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/binding/BindingMappings.java",
                """
                    package org.liteorm.test.selection.binding;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.JdbcValueAdapter;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        adapter = BindingMappings.MoneyAdapter.class
                    )
                    public final class BindingMappings implements JdbcTypeMappings {
                        public static final class MoneyAdapter implements JdbcValueAdapter<Money> {
                            public MoneyAdapter() {}

                            @Override
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }

                            @Override
                            public Money getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
                                return new Money(resultSet.getLong(columnIndex));
                            }
                        }
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/binding/package-info.java",
                """
                    @UseJdbcTypeMappings(BindingMappings.class)
                    package org.liteorm.test.selection.binding;

                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/binding/BindingMapper.java",
                """
                    package org.liteorm.test.selection.binding;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface BindingMapper {
                        @Insert("INSERT INTO account (balance) VALUES (#{balance})")
                        int insert(@Param("balance") Money balance);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/binding/BindingMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "BindingMappings.MoneyAdapter()"), generated);
        assertTrue(generated.contains(
            "statement.setNull(index, java.sql.JDBCType.DECIMAL.getVendorTypeNumber())"), generated);
        assertTrue(generated.contains(
            "jdbcValueAdapter1.setNonNull(statement, index, value, java.sql.JDBCType.DECIMAL)"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("ServiceLoader"), generated);
    }

    @Test
    void generatesDirectResultReadingThroughSelectedAdapter() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/reading/Money.java",
                """
                    package org.liteorm.test.selection.reading;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/reading/ReadingMappings.java",
                """
                    package org.liteorm.test.selection.reading;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.JdbcValueAdapter;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        adapter = ReadingMappings.MoneyAdapter.class
                    )
                    public final class ReadingMappings implements JdbcTypeMappings {
                        public static final class MoneyAdapter implements JdbcValueAdapter<Money> {
                            public MoneyAdapter() {}

                            @Override
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }

                            @Override
                            public Money getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
                                long value = resultSet.getLong(columnIndex);
                                return resultSet.wasNull() ? null : new Money(value);
                            }
                        }
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/reading/package-info.java",
                """
                    @UseJdbcTypeMappings(ReadingMappings.class)
                    package org.liteorm.test.selection.reading;

                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/reading/ReadingMapper.java",
                """
                    package org.liteorm.test.selection.reading;

                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface ReadingMapper {
                        @Select("SELECT balance FROM account")
                        Money findBalance();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/reading/ReadingMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "ReadingMappings.MoneyAdapter()"), generated);
        assertTrue(generated.contains(
            "return jdbcValueAdapter1.getNullable(resultSet, 1);"), generated);
        assertFalse(generated.contains("ParameterBinder<Money>"), generated);
        assertFalse(generated.contains("bindJdbcValue1"), generated);
        assertTrue(generated.contains("this::readFindBalanceResult"), generated);
        assertTrue(generated.contains(
            "return (org.liteorm.test.selection.reading.Money)resultRow[0];"), generated);
    }

    @Test
    void wiresSelectedAdapterForDynamicPropertiesAndForeachItems() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/dynamic/Money.java",
                """
                    package org.liteorm.test.selection.dynamic;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/dynamic/MoneyRequest.java",
                """
                    package org.liteorm.test.selection.dynamic;

                    import java.util.List;

                    public record MoneyRequest(Money money, List<Money> monies) {
                    }
                    """),
            mappingCollection(
                "org.liteorm.test.selection.dynamic", "DynamicMappings", "Money"),
            packageSelection(
                "org.liteorm.test.selection.dynamic", "DynamicMappings"),
            new SourceFile(
                "org/liteorm/test/selection/dynamic/DynamicMapper.java",
                """
                    package org.liteorm.test.selection.dynamic;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface DynamicMapper {
                        @Insert("<script>INSERT INTO account (balance) VALUES (#{request.money})</script>")
                        int insertOne(@Param("request") MoneyRequest request);

                        @Insert("<script>INSERT INTO account (balance) VALUES "
                            + "<foreach collection=\\\"request.monies\\\" item=\\\"money\\\" separator=\\\",\\\">"
                            + "(#{money})</foreach></script>")
                        int insertMany(@Param("request") MoneyRequest request);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/dynamic/DynamicMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "DynamicMappings.MoneyAdapter()"), generated);
        assertEquals(2, countOccurrences(generated,
            "binders.add(jdbcValueParameterBinder1)"), generated);
    }

    @Test
    void keepsAdapterFieldsDistinctForJavaTypesWithTheSameSimpleName() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "example/first/Money.java",
                """
                    package example.first;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "example/second/Money.java",
                """
                    package example.second;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/collision/CollisionMappings.java",
                """
                    package org.liteorm.test.selection.collision;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.JdbcValueAdapter;

                    @JdbcTypeMapping(
                        javaType = example.first.Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        adapter = CollisionMappings.FirstMoneyAdapter.class
                    )
                    @JdbcTypeMapping(
                        javaType = example.second.Money.class,
                        jdbcType = JDBCType.BIGINT,
                        adapter = CollisionMappings.SecondMoneyAdapter.class
                    )
                    public final class CollisionMappings implements JdbcTypeMappings {
                        public static final class FirstMoneyAdapter
                                implements JdbcValueAdapter<example.first.Money> {
                            public FirstMoneyAdapter() {}
                            public void setNonNull(PreparedStatement statement, int index,
                                    example.first.Money value, JDBCType jdbcType) throws SQLException {}
                            public example.first.Money getNullable(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }

                        public static final class SecondMoneyAdapter
                                implements JdbcValueAdapter<example.second.Money> {
                            public SecondMoneyAdapter() {}
                            public void setNonNull(PreparedStatement statement, int index,
                                    example.second.Money value, JDBCType jdbcType) throws SQLException {}
                            public example.second.Money getNullable(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }
                    }
                    """),
            packageSelection(
                "org.liteorm.test.selection.collision", "CollisionMappings"),
            new SourceFile(
                "org/liteorm/test/selection/collision/CollisionMapper.java",
                """
                    package org.liteorm.test.selection.collision;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface CollisionMapper {
                        @Insert("INSERT INTO account (first_value, second_value) VALUES (#{first}, #{second})")
                        int insert(
                            @Param("first") example.first.Money first,
                            @Param("second") example.second.Money second
                        );
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/collision/CollisionMapperImpl.java"));
        assertTrue(generated.contains("jdbcValueAdapter1"), generated);
        assertTrue(generated.contains("jdbcValueAdapter2"), generated);
        assertEquals(1, countOccurrences(generated, "FirstMoneyAdapter()"), generated);
        assertEquals(1, countOccurrences(generated, "SecondMoneyAdapter()"), generated);
    }

    @Test
    void resolvesAndUsesJdbcTypeMappingsFromAnOrdinaryDependency() throws Exception {
        Path dependencyClasses = compileDependency(
            new SourceFile(
                "example/dependency/Money.java",
                """
                    package example.dependency;

                    public record Money(long minorUnits) {
                    }
                    """),
            new SourceFile(
                "example/dependency/DependencyMappings.java",
                """
                    package example.dependency;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.JdbcValueAdapter;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        adapter = DependencyMappings.MoneyAdapter.class
                    )
                    public final class DependencyMappings implements JdbcTypeMappings {
                        public static final class MoneyAdapter implements JdbcValueAdapter<Money> {
                            public MoneyAdapter() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }
                            public Money getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
                                return new Money(resultSet.getLong(columnIndex));
                            }
                        }
                    }
                    """));

        Compilation result = compile(
            dependencyClasses,
            new SourceFile(
                "org/liteorm/test/selection/dependency/package-info.java",
                """
                    @UseJdbcTypeMappings(DependencyMappings.class)
                    package org.liteorm.test.selection.dependency;

                    import example.dependency.DependencyMappings;
                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/dependency/DependencyMapper.java",
                """
                    package org.liteorm.test.selection.dependency;

                    import example.dependency.Money;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface DependencyMapper {
                        @Insert("INSERT INTO account (balance) VALUES (#{balance})")
                        int insert(@Param("balance") Money balance);

                        @Select("SELECT balance FROM account")
                        Money findBalance();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/dependency/DependencyMapperImpl.java"));
        assertTrue(generated.contains("return example.dependency.DependencyMappings.class;"), generated);
        assertEquals(1, countOccurrences(generated,
            "new example.dependency.DependencyMappings.MoneyAdapter()"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.DECIMAL"), generated);
        assertTrue(generated.contains("jdbcValueAdapter1.getNullable(resultSet, 1)"), generated);
    }

    @Test
    void rejectsMalformedJdbcTypeMappingsFromADependencyWithMapperContext() throws Exception {
        Path dependencyClasses = compileDependency(new SourceFile(
            "example/malformed/MalformedDependencyMappings.java",
            """
                package example.malformed;

                import org.liteorm.api.JdbcTypeMappings;

                public class MalformedDependencyMappings implements JdbcTypeMappings {
                }
                """));

        Compilation result = compile(
            dependencyClasses,
            new SourceFile(
                "org/liteorm/test/selection/invaliddependency/package-info.java",
                """
                    @UseJdbcTypeMappings(MalformedDependencyMappings.class)
                    package org.liteorm.test.selection.invaliddependency;

                    import example.malformed.MalformedDependencyMappings;
                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/invaliddependency/InvalidDependencyMapper.java",
                """
                    package org.liteorm.test.selection.invaliddependency;

                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    interface InvalidDependencyMapper {
                        @Select("SELECT name FROM users")
                        String findName();
                    }
                    """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "example.malformed.MalformedDependencyMappings must be a public final JDBC type mappings class"),
            result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.invaliddependency.InvalidDependencyMapper"),
            result::diagnosticsText);
    }

    @Test
    void allowsDifferentMapperPackagesToSelectDifferentCollections() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/alpha/AlphaMappings.java",
                """
                    package org.liteorm.test.selection.alpha;
                    public final class AlphaMappings implements org.liteorm.api.JdbcTypeMappings {}
                    """),
            new SourceFile(
                "org/liteorm/test/selection/alpha/package-info.java",
                """
                    @org.liteorm.annotation.UseJdbcTypeMappings(AlphaMappings.class)
                    package org.liteorm.test.selection.alpha;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/alpha/AlphaMapper.java",
                """
                    package org.liteorm.test.selection.alpha;
                    @org.liteorm.annotation.Mapper
                    public interface AlphaMapper {
                        @org.liteorm.annotation.Select("SELECT name FROM users") String findName();
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/beta/BetaMappings.java",
                """
                    package org.liteorm.test.selection.beta;
                    public final class BetaMappings implements org.liteorm.api.JdbcTypeMappings {}
                    """),
            new SourceFile(
                "org/liteorm/test/selection/beta/package-info.java",
                """
                    @org.liteorm.annotation.UseJdbcTypeMappings(BetaMappings.class)
                    package org.liteorm.test.selection.beta;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/beta/BetaMapper.java",
                """
                    package org.liteorm.test.selection.beta;
                    @org.liteorm.annotation.Mapper
                    public interface BetaMapper {
                        @org.liteorm.annotation.Select("SELECT name FROM users") String findName();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String alpha = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/alpha/AlphaMapperImpl.java"));
        String beta = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/beta/BetaMapperImpl.java"));
        assertTrue(alpha.contains("return AlphaMappings.class;"), alpha);
        assertTrue(beta.contains("return BetaMappings.class;"), beta);
    }

    private SourceFile mappingCollection(String packageName, String mappingsName, String javaTypeName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/" + mappingsName + ".java",
            """
                package %s;

                import java.sql.JDBCType;
                import java.sql.PreparedStatement;
                import java.sql.ResultSet;
                import java.sql.SQLException;
                import org.liteorm.annotation.JdbcTypeMapping;
                import org.liteorm.api.JdbcTypeMappings;
                import org.liteorm.api.JdbcValueAdapter;

                @JdbcTypeMapping(
                    javaType = %s.class,
                    jdbcType = JDBCType.DECIMAL,
                    adapter = %s.MoneyAdapter.class
                )
                public final class %s implements JdbcTypeMappings {
                    public static final class MoneyAdapter implements JdbcValueAdapter<%s> {
                        public MoneyAdapter() {}

                        public void setNonNull(
                                PreparedStatement statement, int index, %s value, JDBCType jdbcType)
                                throws SQLException {}

                        public %s getNullable(ResultSet resultSet, int columnIndex) {
                            return null;
                        }
                    }
                }
                """.formatted(
                    packageName, javaTypeName, mappingsName, mappingsName,
                    javaTypeName, javaTypeName, javaTypeName));
    }

    private SourceFile packageSelection(String packageName, String mappingsName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/package-info.java",
            """
                @UseJdbcTypeMappings(%s.class)
                package %s;

                import org.liteorm.annotation.UseJdbcTypeMappings;
                """.formatted(mappingsName, packageName));
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private Compilation compile(SourceFile... sources) throws Exception {
        return compile(null, sources);
    }

    private Compilation compile(Path additionalClasspath, SourceFile... sources) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);

        for (SourceFile source : sources) {
            Path sourcePath = sourceDirectory.resolve(source.relativePath());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, source.content(), StandardCharsets.UTF_8);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            List<Path> sourcePaths = java.util.Arrays.stream(sources)
                .map(source -> sourceDirectory.resolve(source.relativePath()))
                .toList();
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourcePaths);
            String classpath = System.getProperty("java.class.path");
            if (additionalClasspath != null) {
                classpath += File.pathSeparator + additionalClasspath;
            }
            List<String> options = List.of(
                "-classpath", classpath,
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString());
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            boolean succeeded = task.call();
            return new Compilation(succeeded, diagnostics.getDiagnostics(), generatedDirectory);
        }
    }

    private Path compileDependency(SourceFile... sources) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("dependency-sources");
        Path classesDirectory = temporaryDirectory.resolve("dependency-classes");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);
        List<Path> sourcePaths = new java.util.ArrayList<>();
        for (SourceFile source : sources) {
            Path sourcePath = sourceDirectory.resolve(source.relativePath());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, source.content(), StandardCharsets.UTF_8);
            sourcePaths.add(sourcePath);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourcePaths);
            boolean succeeded = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of(
                    "-proc:none",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDirectory.toString()),
                null,
                units
            ).call();
            assertTrue(succeeded, () -> diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
                .collect(java.util.stream.Collectors.joining("\n")));
        }
        return classesDirectory;
    }

    private record SourceFile(String relativePath, String content) {
    }

    private record Compilation(
            boolean succeeded,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Path generatedDirectory) {

        String diagnosticsText() {
            return diagnostics.stream()
                .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
                .collect(java.util.stream.Collectors.joining("\n"));
        }
    }
}

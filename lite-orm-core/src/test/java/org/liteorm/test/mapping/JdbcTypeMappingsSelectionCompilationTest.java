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
    void userOverrideReplacesTheBaseMappingForTheSameJavaAndJdbcType() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/override/Money.java",
                """
                    package org.liteorm.test.selection.override;

                    public record Money(long minorUnits) {
                    }
                    """),
            mappingCollection(
                "org.liteorm.test.selection.override", "BaseMappings", "Money"),
            new SourceFile(
                "org/liteorm/test/selection/override/ApplicationMappings.java",
                """
                    package org.liteorm.test.selection.override;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        handler = ApplicationMappings.ApplicationMoneyTypeHandler.class
                    )
                    public final class ApplicationMappings implements JdbcTypeMappings {
                        public static final class ApplicationMoneyTypeHandler implements TypeHandler<Money> {
                            public ApplicationMoneyTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {}
                            public Money getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/override/package-info.java",
                """
                    @UseJdbcTypeMappings(
                        value = BaseMappings.class,
                        overrides = ApplicationMappings.class
                    )
                    package org.liteorm.test.selection.override;

                    import org.liteorm.annotation.UseJdbcTypeMappings;
                    """),
            new SourceFile(
                "org/liteorm/test/selection/override/OverrideMapper.java",
                """
                    package org.liteorm.test.selection.override;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface OverrideMapper {
                        @Insert("INSERT INTO account (balance) VALUES (#{balance,jdbcType=DECIMAL})")
                        int insert(@Param("balance") Money balance);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/override/OverrideMapperImpl.java"));
        assertTrue(generated.contains("ApplicationMappings.ApplicationMoneyTypeHandler()"), generated);
        assertFalse(generated.contains("BaseMappings.MoneyTypeHandler()"), generated);
    }

    @Test
    void userOverrideAddsANewMappingAfterTheBaseMappings() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/overrideaddition/Money.java",
                """
                    package org.liteorm.test.selection.overrideaddition;
                    public record Money(long minorUnits) {}
                    """),
            mappingCollection(
                "org.liteorm.test.selection.overrideaddition", "BaseMappings", "Money"),
            new SourceFile(
                "org/liteorm/test/selection/overrideaddition/ApplicationMappings.java",
                """
                    package org.liteorm.test.selection.overrideaddition;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = String.class,
                        jdbcType = JDBCType.VARCHAR,
                        handler = ApplicationMappings.StringTypeHandler.class
                    )
                    public final class ApplicationMappings implements JdbcTypeMappings {
                        public static final class StringTypeHandler implements TypeHandler<String> {
                            public StringTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, String value, JDBCType jdbcType)
                                    throws SQLException {}
                            public String getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }
                    }
                    """),
            packageSelectionWithOverride(
                "org.liteorm.test.selection.overrideaddition", "BaseMappings", "ApplicationMappings"),
            new SourceFile(
                "org/liteorm/test/selection/overrideaddition/OverrideAdditionMapper.java",
                """
                    package org.liteorm.test.selection.overrideaddition;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface OverrideAdditionMapper {
                        @Insert("INSERT INTO account (balance, label) VALUES (#{money}, #{label})")
                        int insert(@Param("money") Money money, @Param("label") String label);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/overrideaddition/OverrideAdditionMapperImpl.java"));
        assertTrue(generated.contains("BaseMappings.MoneyTypeHandler()"), generated);
        assertTrue(generated.contains("ApplicationMappings.StringTypeHandler()"), generated);
    }

    @Test
    void addedOverrideMappingDoesNotChangeTheBaseCanonicalJdbcType() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/overridecanonical/BaseMappings.java",
                """
                    package org.liteorm.test.selection.overridecanonical;

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
                        jdbcType = JDBCType.CHAR,
                        handler = BaseMappings.CharUuidTypeHandler.class
                    )
                    public final class BaseMappings implements JdbcTypeMappings {
                        public static final class CharUuidTypeHandler implements TypeHandler<UUID> {
                            public CharUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/overridecanonical/ApplicationMappings.java",
                """
                    package org.liteorm.test.selection.overridecanonical;

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
                        handler = ApplicationMappings.OtherUuidTypeHandler.class
                    )
                    public final class ApplicationMappings implements JdbcTypeMappings {
                        public static final class OtherUuidTypeHandler implements TypeHandler<UUID> {
                            public OtherUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }
                    }
                    """),
            packageSelectionWithOverride(
                "org.liteorm.test.selection.overridecanonical", "BaseMappings", "ApplicationMappings"),
            new SourceFile(
                "org/liteorm/test/selection/overridecanonical/OverrideCanonicalMapper.java",
                """
                    package org.liteorm.test.selection.overridecanonical;

                    import java.util.UUID;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface OverrideCanonicalMapper {
                        @Insert("INSERT INTO values_table (uuid_value) VALUES (#{value})")
                        int insertCanonical(@Param("value") UUID value);

                        @Insert("INSERT INTO values_table (uuid_value) VALUES (#{value,jdbcType=OTHER})")
                        int insertOther(@Param("value") UUID value);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/overridecanonical/OverrideCanonicalMapperImpl.java"));
        assertTrue(generated.contains("new BaseMappings.CharUuidTypeHandler()"), generated);
        assertTrue(generated.contains("new ApplicationMappings.OtherUuidTypeHandler()"), generated);
    }

    @Test
    void rejectsMoreThanOneUserOverrideCollection() throws Exception {
        Compilation result = compile(
            emptyMappingCollection("org.liteorm.test.selection.multipleoverrides", "BaseMappings"),
            emptyMappingCollection("org.liteorm.test.selection.multipleoverrides", "FirstOverrides"),
            emptyMappingCollection("org.liteorm.test.selection.multipleoverrides", "SecondOverrides"),
            new SourceFile(
                "org/liteorm/test/selection/multipleoverrides/package-info.java",
                """
                    @org.liteorm.annotation.UseJdbcTypeMappings(
                        value = BaseMappings.class,
                        overrides = {FirstOverrides.class, SecondOverrides.class}
                    )
                    package org.liteorm.test.selection.multipleoverrides;
                    """),
            simpleSelectMapper("org.liteorm.test.selection.multipleoverrides", "MultipleOverridesMapper"));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "at most one override JdbcTypeMappings collection may be selected"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.multipleoverrides.MultipleOverridesMapper"), result::diagnosticsText);
    }

    @Test
    void rejectsMalformedUserOverrideWithPackageAndMapperContext() throws Exception {
        Compilation result = compile(
            emptyMappingCollection("org.liteorm.test.selection.invalidoverride", "BaseMappings"),
            new SourceFile(
                "org/liteorm/test/selection/invalidoverride/MalformedOverrides.java",
                """
                    package org.liteorm.test.selection.invalidoverride;
                    public class MalformedOverrides implements org.liteorm.api.JdbcTypeMappings {}
                    """),
            packageSelectionWithOverride(
                "org.liteorm.test.selection.invalidoverride", "BaseMappings", "MalformedOverrides"),
            simpleSelectMapper("org.liteorm.test.selection.invalidoverride", "InvalidOverrideMapper"));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "MalformedOverrides must be a public final JDBC type mappings class"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Mapper package org.liteorm.test.selection.invalidoverride"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "org.liteorm.test.selection.invalidoverride.InvalidOverrideMapper"), result::diagnosticsText);
    }

    @Test
    void doesNotImplicitlyAppendCoreStandardMappings() throws Exception {
        Compilation result = compile(
            emptyMappingCollection("org.liteorm.test.selection.noimplicitstandard", "EmptyMappings"),
            packageSelection("org.liteorm.test.selection.noimplicitstandard", "EmptyMappings"),
            new SourceFile(
                "org/liteorm/test/selection/noimplicitstandard/NoImplicitStandardMapper.java",
                """
                    package org.liteorm.test.selection.noimplicitstandard;

                    import java.math.BigInteger;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface NoImplicitStandardMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insert(@Param("value") BigInteger value);
                    }
                    """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "unsupported type java.math.BigInteger and requires @UseParameterBinder"),
            result::diagnosticsText);
    }

    @Test
    void generatesDirectNullSafeParameterBindingThroughSelectedTypeHandler() throws Exception {
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
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        handler = BindingMappings.MoneyTypeHandler.class
                    )
                    public final class BindingMappings implements JdbcTypeMappings {
                        public static final class MoneyTypeHandler implements TypeHandler<Money> {
                            public MoneyTypeHandler() {}

                            @Override
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }

                            @Override
                            public Money getResult(ResultSet resultSet, int columnIndex) throws SQLException {
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
            "BindingMappings.MoneyTypeHandler()"), generated);
        assertTrue(generated.contains(
            "JdbcTypeRouter.mapping(Money.class, java.sql.JDBCType.DECIMAL, typeHandler1)"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(org.liteorm.test.selection.binding.Money.class, "
                + "java.sql.JDBCType.DECIMAL)"), generated);
        assertFalse(generated.contains("Class.forName"), generated);
        assertFalse(generated.contains("ServiceLoader"), generated);
    }

    @Test
    void generatesDirectResultReadingThroughSelectedTypeHandler() throws Exception {
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
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        handler = ReadingMappings.MoneyTypeHandler.class
                    )
                    public final class ReadingMappings implements JdbcTypeMappings {
                        public static final class MoneyTypeHandler implements TypeHandler<Money> {
                            public MoneyTypeHandler() {}

                            @Override
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }

                            @Override
                            public Money getResult(ResultSet resultSet, int columnIndex) throws SQLException {
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
            "ReadingMappings.MoneyTypeHandler()"), generated);
        assertTrue(generated.contains(
            "JdbcTypeRouter.mapping(Money.class, java.sql.JDBCType.DECIMAL, typeHandler1)"), generated);
        assertFalse(generated.contains("ParameterBinder<Money>"), generated);
        assertFalse(generated.contains("bindJdbcValue1"), generated);
        assertTrue(generated.contains(
            "new Class<?>[]{org.liteorm.test.selection.reading.Money.class}, null"), generated);
        assertTrue(generated.contains(
            "return (org.liteorm.test.selection.reading.Money)resultRow[0];"), generated);
    }

    @Test
    void composesSelectedValueTypeHandlersIntoGeneratedRecordAndJavaBeanMappings() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/compositevalue/Money.java",
                """
                    package org.liteorm.test.selection.compositevalue;
                    public record Money(long minorUnits) {}
                    """),
            mappingCollection(
                "org.liteorm.test.selection.compositevalue", "CompositeMappings", "Money"),
            packageSelection("org.liteorm.test.selection.compositevalue", "CompositeMappings"),
            new SourceFile(
                "org/liteorm/test/selection/compositevalue/AccountRecord.java",
                """
                    package org.liteorm.test.selection.compositevalue;
                    public record AccountRecord(long id, Money balance) {}
                    """),
            new SourceFile(
                "org/liteorm/test/selection/compositevalue/AccountBean.java",
                """
                    package org.liteorm.test.selection.compositevalue;

                    public class AccountBean {
                        private long id;
                        private Money balance;

                        public AccountBean() {}
                        public void setId(long id) { this.id = id; }
                        public void setBalance(Money balance) { this.balance = balance; }
                    }
                    """),
            new SourceFile(
                "org/liteorm/test/selection/compositevalue/CompositeValueMapper.java",
                """
                    package org.liteorm.test.selection.compositevalue;

                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface CompositeValueMapper {
                        @Select("SELECT id, balance FROM account")
                        AccountRecord findRecord();

                        @Select("SELECT id, balance FROM account")
                        AccountBean findBean();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/compositevalue/CompositeValueMapperImpl.java"));
        assertEquals(1, countOccurrences(generated, "CompositeMappings.MoneyTypeHandler()"), generated);
        assertEquals(2, countOccurrences(generated,
            "new Class<?>[]{long.class, org.liteorm.test.selection.compositevalue.Money.class}"), generated);
        assertTrue(generated.contains("new String[]{\"id\", \"balance\"}"), generated);
    }

    @Test
    void wiresSelectedTypeHandlerForDynamicPropertiesAndForeachItems() throws Exception {
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
            "DynamicMappings.MoneyTypeHandler()"), generated);
        assertEquals(2, countOccurrences(generated,
            "binders.add(jdbcTypeRouter.parameterBinder("), generated);
    }

    @Test
    void keepsTypeHandlerFieldsDistinctForJavaTypesWithTheSameSimpleName() throws Exception {
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
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = example.first.Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        handler = CollisionMappings.FirstMoneyTypeHandler.class
                    )
                    @JdbcTypeMapping(
                        javaType = example.second.Money.class,
                        jdbcType = JDBCType.BIGINT,
                        handler = CollisionMappings.SecondMoneyTypeHandler.class
                    )
                    public final class CollisionMappings implements JdbcTypeMappings {
                        public static final class FirstMoneyTypeHandler
                                implements TypeHandler<example.first.Money> {
                            public FirstMoneyTypeHandler() {}
                            public void setNonNull(PreparedStatement statement, int index,
                                    example.first.Money value, JDBCType jdbcType) throws SQLException {}
                            public example.first.Money getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }

                        public static final class SecondMoneyTypeHandler
                                implements TypeHandler<example.second.Money> {
                            public SecondMoneyTypeHandler() {}
                            public void setNonNull(PreparedStatement statement, int index,
                                    example.second.Money value, JDBCType jdbcType) throws SQLException {}
                            public example.second.Money getResult(ResultSet resultSet, int columnIndex) {
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
        assertTrue(generated.contains("typeHandler1"), generated);
        assertTrue(generated.contains("typeHandler2"), generated);
        assertEquals(1, countOccurrences(generated, "FirstMoneyTypeHandler()"), generated);
        assertEquals(1, countOccurrences(generated, "SecondMoneyTypeHandler()"), generated);
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
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.DECIMAL,
                        handler = DependencyMappings.MoneyTypeHandler.class
                    )
                    public final class DependencyMappings implements JdbcTypeMappings {
                        public static final class MoneyTypeHandler implements TypeHandler<Money> {
                            public MoneyTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {
                                statement.setLong(index, value.minorUnits());
                            }
                            public Money getResult(ResultSet resultSet, int columnIndex) throws SQLException {
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
            "new example.dependency.DependencyMappings.MoneyTypeHandler()"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.DECIMAL"), generated);
        assertTrue(generated.contains(
            "new Class<?>[]{example.dependency.Money.class}, null"), generated);
    }

    @Test
    void selectsMappingByDeclaredJdbcTypeForTheSameJavaType() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/declaredtype/DeclaredTypeMappings.java",
                """
                    package org.liteorm.test.selection.declaredtype;

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
                        jdbcType = JDBCType.CHAR,
                        handler = DeclaredTypeMappings.CharUuidTypeHandler.class
                    )
                    @JdbcTypeMapping(
                        javaType = UUID.class,
                        jdbcType = JDBCType.BINARY,
                        handler = DeclaredTypeMappings.BinaryUuidTypeHandler.class
                    )
                    public final class DeclaredTypeMappings implements JdbcTypeMappings {
                        public static final class CharUuidTypeHandler implements TypeHandler<UUID> {
                            public CharUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }

                        public static final class BinaryUuidTypeHandler implements TypeHandler<UUID> {
                            public BinaryUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }
                    }
                    """),
            packageSelection(
                "org.liteorm.test.selection.declaredtype", "DeclaredTypeMappings"),
            new SourceFile(
                "org/liteorm/test/selection/declaredtype/DeclaredTypeMapper.java",
                """
                    package org.liteorm.test.selection.declaredtype;

                    import java.util.UUID;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface DeclaredTypeMapper {
                        @Insert("INSERT INTO values_table (uuid_value) VALUES (#{value,jdbcType=CHAR})")
                        int insertChar(@Param("value") UUID value);

                        @Insert("INSERT INTO values_table (uuid_value) VALUES (#{value,jdbcType=BINARY})")
                        int insertBinary(@Param("value") UUID value);

                        @Insert("<script>INSERT INTO values_table (uuid_value) VALUES ("
                            + "<if test=\\\"value != null\\\">#{value,jdbcType=BINARY}</if>)</script>")
                        int insertDynamicBinary(@Param("value") UUID value);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/declaredtype/DeclaredTypeMapperImpl.java"));
        assertEquals(1, countOccurrences(generated, "new DeclaredTypeMappings.CharUuidTypeHandler()"), generated);
        assertEquals(1, countOccurrences(generated, "new DeclaredTypeMappings.BinaryUuidTypeHandler()"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.util.UUID.class, java.sql.JDBCType.CHAR)"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.util.UUID.class, java.sql.JDBCType.BINARY)"), generated);
    }

    @Test
    void prefersGenericParameterRouteWhenAVendorResultRouteSharesTheJdbcType() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/vendorroute/VendorRouteMappings.java",
                """
                    package org.liteorm.test.selection.vendorroute;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.TypeHandler;

                    record Money(long minorUnits) {}

                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.OTHER,
                        handler = VendorRouteMappings.GenericMoneyTypeHandler.class
                    )
                    @JdbcTypeMapping(
                        javaType = Money.class,
                        jdbcType = JDBCType.OTHER,
                        vendorTypeName = "money",
                        handler = VendorRouteMappings.VendorMoneyTypeHandler.class
                    )
                    public final class VendorRouteMappings implements JdbcTypeMappings {
                        public static final class GenericMoneyTypeHandler implements TypeHandler<Money> {
                            public GenericMoneyTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {}
                            public Money getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }

                        public static final class VendorMoneyTypeHandler implements TypeHandler<Money> {
                            public VendorMoneyTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, Money value, JDBCType jdbcType)
                                    throws SQLException {}
                            public Money getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }
                    }
                    """),
            packageSelection(
                "org.liteorm.test.selection.vendorroute", "VendorRouteMappings"),
            new SourceFile(
                "org/liteorm/test/selection/vendorroute/VendorRouteMapper.java",
                """
                    package org.liteorm.test.selection.vendorroute;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface VendorRouteMapper {
                        @Insert("INSERT INTO values_table (amount) VALUES (#{value})")
                        int insert(@Param("value") Money value);

                        @Insert("INSERT INTO values_table (amount) VALUES (#{value,jdbcType=OTHER})")
                        int insertExplicit(@Param("value") Money value);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/vendorroute/VendorRouteMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "new VendorRouteMappings.GenericMoneyTypeHandler()"), generated);
        assertEquals(1, countOccurrences(generated,
            "new VendorRouteMappings.VendorMoneyTypeHandler()"), generated);
        assertEquals(2, countOccurrences(generated,
            "jdbcTypeRouter.parameterBinder(org.liteorm.test.selection.vendorroute.Money.class, java.sql.JDBCType.OTHER)"),
            generated);
    }

    @Test
    void rejectsAmbiguousMappingsWhenJdbcTypeIsNotDeclared() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/ambiguous/AmbiguousMappings.java",
                """
                    package org.liteorm.test.selection.ambiguous;

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
                        jdbcType = JDBCType.CHAR,
                        handler = AmbiguousMappings.CharUuidTypeHandler.class
                    )
                    @JdbcTypeMapping(
                        javaType = UUID.class,
                        jdbcType = JDBCType.BINARY,
                        handler = AmbiguousMappings.BinaryUuidTypeHandler.class
                    )
                    public final class AmbiguousMappings implements JdbcTypeMappings {
                        public static final class CharUuidTypeHandler implements TypeHandler<UUID> {
                            public CharUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }

                        public static final class BinaryUuidTypeHandler implements TypeHandler<UUID> {
                            public BinaryUuidTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, UUID value, JDBCType jdbcType)
                                    throws SQLException {}
                            public UUID getResult(ResultSet resultSet, int columnIndex) {
                                return null;
                            }
                        }
                    }
                    """),
            packageSelection(
                "org.liteorm.test.selection.ambiguous", "AmbiguousMappings"),
            new SourceFile(
                "org/liteorm/test/selection/ambiguous/AmbiguousMapper.java",
                """
                    package org.liteorm.test.selection.ambiguous;

                    import java.util.UUID;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface AmbiguousMapper {
                        @Insert("INSERT INTO values_table (uuid_value) VALUES (#{value})")
                        int insert(@Param("value") UUID value);
                    }
                    """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "AmbiguousMapper#insert: JDBC parameter expression value of type java.util.UUID matches"
                + " multiple JDBC type mappings [CHAR, BINARY]; declare jdbcType explicitly"),
            result::diagnosticsText);
    }

    @Test
    void infersCanonicalJdbcTypeWhenOneOfSeveralMappingsIsCanonical() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/canonical/CanonicalMappings.java",
                """
                    package org.liteorm.test.selection.canonical;

                    import java.sql.JDBCType;
                    import java.sql.PreparedStatement;
                    import java.sql.ResultSet;
                    import java.sql.SQLException;
                    import org.liteorm.annotation.JdbcTypeMapping;
                    import org.liteorm.api.JdbcTypeMappings;
                    import org.liteorm.api.TypeHandler;

                    @JdbcTypeMapping(
                        javaType = String.class,
                        jdbcType = JDBCType.CHAR,
                        handler = CanonicalMappings.CharStringTypeHandler.class
                    )
                    @JdbcTypeMapping(
                        javaType = String.class,
                        jdbcType = JDBCType.VARCHAR,
                        handler = CanonicalMappings.VarcharStringTypeHandler.class
                    )
                    public final class CanonicalMappings implements JdbcTypeMappings {
                        public static final class CharStringTypeHandler implements TypeHandler<String> {
                            public CharStringTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, String value, JDBCType jdbcType)
                                    throws SQLException {}
                            public String getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }

                        public static final class VarcharStringTypeHandler implements TypeHandler<String> {
                            public VarcharStringTypeHandler() {}
                            public void setNonNull(
                                    PreparedStatement statement, int index, String value, JDBCType jdbcType)
                                    throws SQLException {}
                            public String getResult(ResultSet resultSet, int columnIndex) { return null; }
                        }
                    }
                    """),
            packageSelection("org.liteorm.test.selection.canonical", "CanonicalMappings"),
            new SourceFile(
                "org/liteorm/test/selection/canonical/CanonicalMapper.java",
                """
                    package org.liteorm.test.selection.canonical;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    @Mapper
                    public interface CanonicalMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertCanonical(@Param("value") String value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value,jdbcType=CHAR})")
                        int insertChar(@Param("value") String value);
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/canonical/CanonicalMapperImpl.java"));
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.lang.String.class, java.sql.JDBCType.VARCHAR)"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.lang.String.class, java.sql.JDBCType.CHAR)"), generated);
    }

    @Test
    void rejectsInvalidJdbcTypeParameterMetadata() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/invalidmetadata/InvalidMetadataMappings.java",
                """
                    package org.liteorm.test.selection.invalidmetadata;

                    import org.liteorm.api.JdbcTypeMappings;

                    public final class InvalidMetadataMappings implements JdbcTypeMappings {
                    }
                    """),
            packageSelection(
                "org.liteorm.test.selection.invalidmetadata", "InvalidMetadataMappings"),
            invalidMetadataMapper("UnknownJdbcTypeMapper", "#{value,jdbcType=NOT_A_JDBC_TYPE}"),
            invalidMetadataMapper("UnsupportedAttributeMapper", "#{value,typeHandler=ExampleHandler}"),
            invalidMetadataMapper("MalformedAttributeMapper", "#{value,jdbcType}"),
            invalidMetadataMapper("UnmappedDeclaredJdbcTypeMapper", "#{value,jdbcType=BIGINT}"),
            invalidMetadataMapper(
                "DuplicateJdbcTypeMapper", "#{value,jdbcType=CHAR,jdbcType=VARCHAR}"),
            new SourceFile(
                "org/liteorm/test/selection/invalidmetadata/UnsupportedEnumJdbcTypeMapper.java",
                """
                    package org.liteorm.test.selection.invalidmetadata;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;

                    enum Status { ACTIVE }

                    @Mapper
                    public interface UnsupportedEnumJdbcTypeMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value,jdbcType=BIGINT})")
                        int insert(@Param("value") Status value);
                    }
                    """));

        assertFalse(result.succeeded(), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Unknown JDBCType in SQL parameter: NOT_A_JDBC_TYPE"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Unsupported SQL parameter attribute: typeHandler"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Malformed SQL parameter attribute: jdbcType"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "Duplicate SQL parameter jdbcType attribute"), result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "no JDBC type mapping exists for java.lang.String + BIGINT"),
            result::diagnosticsText);
        assertTrue(result.diagnosticsText().contains(
            "no JDBC type mapping exists for org.liteorm.test.selection.invalidmetadata.Status + BIGINT"),
            result::diagnosticsText);
    }

    @Test
    void appliesSelectedStandardBigIntegerMapping() throws Exception {
        Compilation result = compile(
            standardPackageSelection("org.liteorm.test.selection.biginteger"),
            new SourceFile(
                "org/liteorm/test/selection/biginteger/BigIntegerMapper.java",
                """
                    package org.liteorm.test.selection.biginteger;

                    import java.math.BigInteger;
                    import java.util.List;
                    import org.liteorm.annotation.Batch;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface BigIntegerMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insert(@Param("value") BigInteger value);

                        @Insert("<script>INSERT INTO values_table (value) "
                            + "<if test=\\\"value != null\\\">VALUES (#{value})</if></script>")
                        int insertDynamic(@Param("value") BigInteger value);

                        @Batch("INSERT INTO values_table (value) VALUES (#{item})")
                        int[] insertBatch(List<BigInteger> values);

                        @Select("SELECT value FROM values_table")
                        BigInteger findValue();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/biginteger/BigIntegerMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "new org.liteorm.jdbc.StandardJdbcTypeMappings.BigIntegerTypeHandler()"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.math.BigInteger.class, java.sql.JDBCType.DECIMAL)"), generated);
        assertTrue(generated.contains("new Class<?>[]{java.math.BigInteger.class}, null"), generated);
    }

    @Test
    void appliesSelectedStandardBoxedByteArrayMapping() throws Exception {
        Compilation result = compile(
            standardPackageSelection("org.liteorm.test.selection.boxedbytes"),
            new SourceFile(
                "org/liteorm/test/selection/boxedbytes/BoxedBytesMapper.java",
                """
                    package org.liteorm.test.selection.boxedbytes;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface BoxedBytesMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insert(@Param("value") Byte[] value);

                        @Select("SELECT value FROM values_table")
                        Byte[] findValue();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/boxedbytes/BoxedBytesMapperImpl.java"));
        assertEquals(1, countOccurrences(generated,
            "new org.liteorm.jdbc.StandardJdbcTypeMappings.BoxedByteArrayTypeHandler()"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(java.lang.Byte[].class, java.sql.JDBCType.VARBINARY)"), generated);
        assertTrue(generated.contains("new Class<?>[]{java.lang.Byte[].class}, null"), generated);
    }

    @Test
    void appliesSelectedStandardLegacyDateMappings() throws Exception {
        Compilation result = compile(
            standardPackageSelection("org.liteorm.test.selection.legacydate"),
            new SourceFile(
                "org/liteorm/test/selection/legacydate/LegacyDateMapper.java",
                """
                    package org.liteorm.test.selection.legacydate;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface LegacyDateMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertUtilDate(@Param("value") java.util.Date value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value,jdbcType=DATE})")
                        int insertUtilDateOnly(@Param("value") java.util.Date value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value,jdbcType=TIME})")
                        int insertUtilTimeOnly(@Param("value") java.util.Date value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertSqlDate(@Param("value") java.sql.Date value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertSqlTime(@Param("value") java.sql.Time value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertSqlTimestamp(@Param("value") java.sql.Timestamp value);

                        @Select("SELECT value FROM values_table")
                        java.util.Date findUtilDate();

                        @Select("SELECT value FROM values_table")
                        java.util.Date findUtilDateOnly();

                        @Select("SELECT value FROM values_table")
                        java.util.Date findUtilTimeOnly();

                        @Select("SELECT value FROM values_table")
                        java.sql.Date findSqlDate();

                        @Select("SELECT value FROM values_table")
                        java.sql.Time findSqlTime();

                        @Select("SELECT value FROM values_table")
                        java.sql.Timestamp findSqlTimestamp();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/legacydate/LegacyDateMapperImpl.java"));
        assertTrue(generated.contains("StandardJdbcTypeMappings.UtilDateTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.UtilDateOnlyTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.UtilTimeOnlyTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.SqlDateTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.SqlTimeTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.SqlTimestampTypeHandler"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.DATE"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.TIME"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.TIMESTAMP"), generated);
    }

    @Test
    void appliesSelectedStandardCalendarValueMappings() throws Exception {
        Compilation result = compile(
            standardPackageSelection("org.liteorm.test.selection.calendar"),
            new SourceFile(
                "org/liteorm/test/selection/calendar/CalendarMapper.java",
                """
                    package org.liteorm.test.selection.calendar;

                    import java.time.Month;
                    import java.time.Year;
                    import java.time.YearMonth;
                    import java.time.chrono.JapaneseDate;
                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    @Mapper
                    public interface CalendarMapper {
                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertYear(@Param("value") Year value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertMonth(@Param("value") Month value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertYearMonth(@Param("value") YearMonth value);

                        @Insert("INSERT INTO values_table (value) VALUES (#{value})")
                        int insertJapaneseDate(@Param("value") JapaneseDate value);

                        @Select("SELECT value FROM values_table")
                        Year findYear();

                        @Select("SELECT value FROM values_table")
                        Month findMonth();

                        @Select("SELECT value FROM values_table")
                        YearMonth findYearMonth();

                        @Select("SELECT value FROM values_table")
                        JapaneseDate findJapaneseDate();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/calendar/CalendarMapperImpl.java"));
        assertTrue(generated.contains("StandardJdbcTypeMappings.YearTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.MonthTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.YearMonthTypeHandler"), generated);
        assertTrue(generated.contains("StandardJdbcTypeMappings.JapaneseDateTypeHandler"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.INTEGER"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.VARCHAR"), generated);
        assertTrue(generated.contains("java.sql.JDBCType.DATE"), generated);
    }

    @Test
    void usesEnumNamesByDefaultAndOrdinalsOnlyWhenExplicitlyDeclared() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/enums/EnumMappings.java",
                """
                    package org.liteorm.test.selection.enums;

                    import org.liteorm.api.JdbcTypeMappings;

                    public final class EnumMappings implements JdbcTypeMappings {
                    }
                    """),
            packageSelection("org.liteorm.test.selection.enums", "EnumMappings"),
            new SourceFile(
                "org/liteorm/test/selection/enums/EnumMapper.java",
                """
                    package org.liteorm.test.selection.enums;

                    import org.liteorm.annotation.Insert;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Param;
                    import org.liteorm.annotation.Select;

                    enum Status { ACTIVE, DISABLED }

                    @Mapper
                    public interface EnumMapper {
                        @Insert("INSERT INTO values_table (name_value) VALUES (#{value})")
                        int insertName(@Param("value") Status value);

                        @Insert("INSERT INTO values_table (ordinal_value) VALUES (#{value,jdbcType=INTEGER})")
                        int insertOrdinal(@Param("value") Status value);

                        @Select("SELECT name_value FROM values_table")
                        Status findName();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/enums/EnumMapperImpl.java"));
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(org.liteorm.test.selection.enums.Status.class, "
                + "java.sql.JDBCType.VARCHAR)"), generated);
        assertTrue(generated.contains(
            "jdbcTypeRouter.parameterBinder(org.liteorm.test.selection.enums.Status.class, "
                + "java.sql.JDBCType.INTEGER)"), generated);
        assertTrue(generated.contains(
            "new Class<?>[]{org.liteorm.test.selection.enums.Status.class}, null"), generated);
    }

    @Test
    void generatesRuntimeMetadataRoutingForScalarEnumResults() throws Exception {
        Compilation result = compile(
            new SourceFile(
                "org/liteorm/test/selection/enumresults/EnumResultMappings.java",
                """
                    package org.liteorm.test.selection.enumresults;

                    import org.liteorm.api.JdbcTypeMappings;

                    public final class EnumResultMappings implements JdbcTypeMappings {
                    }
                    """),
            packageSelection("org.liteorm.test.selection.enumresults", "EnumResultMappings"),
            new SourceFile(
                "org/liteorm/test/selection/enumresults/EnumResultMapper.java",
                """
                    package org.liteorm.test.selection.enumresults;

                    import java.util.List;
                    import java.util.Optional;
                    import org.liteorm.annotation.Mapper;
                    import org.liteorm.annotation.Select;

                    enum Status {
                        ACTIVE,
                        DISABLED;

                        @Override
                        public String toString() {
                            return name().toLowerCase();
                        }
                    }

                    @Mapper
                    public interface EnumResultMapper {
                        @Select("SELECT status FROM values_table")
                        Status findByName();

                        @Select("SELECT status FROM values_table")
                        Status findByOrdinal();

                        @Select("SELECT status FROM values_table")
                        List<Status> findAllByOrdinal();

                        @Select("SELECT status FROM values_table")
                        Optional<Status> findOptionalByOrdinal();
                    }
                    """));

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/liteorm/test/selection/enumresults/EnumResultMapperImpl.java"));
        assertEquals(4, countOccurrences(generated,
            "new Class<?>[]{org.liteorm.test.selection.enumresults.Status.class}, null"), generated);
        assertTrue(generated.contains(
            "org.liteorm.test.selection.enumresults.Status::valueOf"), generated);
        assertTrue(generated.contains(
            "org.liteorm.test.selection.enumresults.Status.values()"), generated);
        assertFalse(generated.contains("getEnumConstants"), generated);
        assertFalse(generated.contains("ResultJdbcType"), generated);
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
                import org.liteorm.api.TypeHandler;

                @JdbcTypeMapping(
                    javaType = %s.class,
                    jdbcType = JDBCType.DECIMAL,
                    handler = %s.MoneyTypeHandler.class
                )
                public final class %s implements JdbcTypeMappings {
                    public static final class MoneyTypeHandler implements TypeHandler<%s> {
                        public MoneyTypeHandler() {}

                        public void setNonNull(
                                PreparedStatement statement, int index, %s value, JDBCType jdbcType)
                                throws SQLException {}

                        public %s getResult(ResultSet resultSet, int columnIndex) {
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

    private SourceFile standardPackageSelection(String packageName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/package-info.java",
            """
                @UseJdbcTypeMappings(StandardJdbcTypeMappings.class)
                package %s;

                import org.liteorm.annotation.UseJdbcTypeMappings;
                import org.liteorm.jdbc.StandardJdbcTypeMappings;
                """.formatted(packageName));
    }

    private SourceFile packageSelectionWithOverride(
            String packageName, String mappingsName, String overrideMappingsName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/package-info.java",
            """
                @UseJdbcTypeMappings(value = %s.class, overrides = %s.class)
                package %s;

                import org.liteorm.annotation.UseJdbcTypeMappings;
                """.formatted(mappingsName, overrideMappingsName, packageName));
    }

    private SourceFile emptyMappingCollection(String packageName, String mappingsName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/" + mappingsName + ".java",
            """
                package %s;
                public final class %s implements org.liteorm.api.JdbcTypeMappings {}
                """.formatted(packageName, mappingsName));
    }

    private SourceFile simpleSelectMapper(String packageName, String mapperName) {
        return new SourceFile(
            packageName.replace('.', '/') + "/" + mapperName + ".java",
            """
                package %s;
                @org.liteorm.annotation.Mapper
                public interface %s {
                    @org.liteorm.annotation.Select("SELECT name FROM users") String findName();
                }
                """.formatted(packageName, mapperName));
    }

    private SourceFile invalidMetadataMapper(String mapperName, String parameterExpression) {
        return new SourceFile(
            "org/liteorm/test/selection/invalidmetadata/" + mapperName + ".java",
            """
                package org.liteorm.test.selection.invalidmetadata;

                import org.liteorm.annotation.Insert;
                import org.liteorm.annotation.Mapper;
                import org.liteorm.annotation.Param;

                @Mapper
                public interface %s {
                    @Insert("INSERT INTO values_table (value) VALUES (%s)")
                    int insert(@Param("value") String value);
                }
                """.formatted(mapperName, parameterExpression));
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

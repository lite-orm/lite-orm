package io.github.kervix.test.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.kervix.api.ConnectionHandle;
import io.github.kervix.api.SqlExecutor;
import io.github.kervix.compile.KervixProcessor;
import io.github.kervix.jdbc.JdbcSqlExecutor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationResultMappingTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsRecordsAndJavaBeansFromResultsAnnotations() throws Exception {
        Compilation compilation = compileFixture();
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        String generated = Files.readString(compilation.generatedDirectory().resolve(
            "org/kervix/test/resultfixture/ResultMapperImpl.java"));
        assertTrue(generated.contains("new io.github.kervix.test.resultfixture.ResultMapper.UserRecord("), generated);
        assertTrue(generated.contains("mapped.setName("), generated);
        assertTrue(generated.contains("new String[]{\"user_id\", \"user_name\"}"), generated);
        assertTrue(generated.contains("private static final QueryDefinition<"
            + "io.github.kervix.test.resultfixture.ResultMapper.UserBean> FIND_BEAN_DEFINITION"), generated);
        assertTrue(generated.contains("private static io.github.kervix.test.resultfixture.ResultMapper.UserBean "
            + "mapFindBeanRow("), generated);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{compilation.classesDirectory().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("io.github.kervix.test.resultfixture.ResultMapper");
            Class<?> implementationType = loader.loadClass("io.github.kervix.test.resultfixture.ResultMapperImpl");
            SqlExecutor executor = jdbcExecutor();
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            Object record = mapperType.getMethod("findRecord").invoke(mapper);
            Object bean = mapperType.getMethod("findBean").invoke(mapper);

            assertEquals(7L, record.getClass().getMethod("id").invoke(record));
            assertEquals("Alice", record.getClass().getMethod("name").invoke(record));
            assertEquals(7L, bean.getClass().getMethod("getId").invoke(bean));
            assertEquals("Alice", bean.getClass().getMethod("getName").invoke(bean));
        }
    }

    private SqlExecutor jdbcExecutor() {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnCount" -> 2;
            case "getColumnLabel", "getColumnName" -> (int) arguments[0] == 1 ? "user_name" : "user_id";
            case "getColumnType" -> (int) arguments[0] == 1
                ? JDBCType.VARCHAR.getVendorTypeNumber() : JDBCType.BIGINT.getVendorTypeNumber();
            case "getColumnTypeName" -> (int) arguments[0] == 1 ? "VARCHAR" : "BIGINT";
            default -> null;
        });
        AtomicInteger row = new AtomicInteger(-1);
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> row.incrementAndGet() == 0;
            case "getString" -> "Alice";
            case "getObject" -> (int) arguments[0] == 1 ? "Alice" : 7L;
            default -> null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "executeQuery" -> {
                row.set(-1);
                yield resultSet;
            }
            default -> null;
        });
        Connection connection = proxy(Connection.class, (method, arguments) ->
            method.equals("prepareStatement") ? statement : null);
        ConnectionHandle handle = new ConnectionHandle() {
            @Override
            public Connection connection() {
                return connection;
            }

            @Override
            public void close() {
            }
        };
        return new JdbcSqlExecutor(() -> handle);
    }

    @Test
    void mapsAScalarFromAnExplicitResultColumn() throws Exception {
        Compilation compilation = compileSource("ScalarMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface ScalarMapper {
                @io.github.kervix.annotation.Select("SELECT count(*) AS total FROM users")
                @io.github.kervix.annotation.Results(@io.github.kervix.annotation.Result(column = "total"))
                long count();
            }
            """);

        assertTrue(compilation.succeeded(), compilation::diagnosticsText);
        String generated = Files.readString(compilation.generatedDirectory().resolve(
            "org/kervix/test/resultfixture/ScalarMapperImpl.java"));
        assertTrue(generated.contains("new String[]{\"total\"}"), generated);
    }

    @Test
    void mapsAJavaBeanPropertyThroughItsSetterRatherThanItsBackingField() throws Exception {
        Compilation compilation = compileSource("SetterPropertyMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface SetterPropertyMapper {
                @io.github.kervix.annotation.Select("SELECT user_id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(property = "id", column = "user_id"))
                User find();

                class User {
                    private Long identifier;

                    public User() {}
                    public Long getId() { return identifier; }
                    public void setId(Long id) { this.identifier = id; }
                }
            }
            """);

        assertTrue(compilation.succeeded(), compilation::diagnosticsText);
        String generated = Files.readString(compilation.generatedDirectory().resolve(
            "org/kervix/test/resultfixture/SetterPropertyMapperImpl.java"));
        assertTrue(generated.contains("mapped.setId("), generated);
        assertTrue(generated.contains("new String[]{\"user_id\"}"), generated);
    }

    @Test
    void preservesJavaBeanAcronymPropertyNames() throws Exception {
        Compilation compilation = compileSource("AcronymPropertyMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface AcronymPropertyMapper {
                @io.github.kervix.annotation.Select("SELECT profile_url FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(property = "URL", column = "profile_url"))
                User find();

                class User {
                    private String URL;

                    public User() {}
                    public String getURL() { return URL; }
                    public void setURL(String URL) { this.URL = URL; }
                }
            }
            """);

        assertTrue(compilation.succeeded(), compilation::diagnosticsText);
        String generated = Files.readString(compilation.generatedDirectory().resolve(
            "org/kervix/test/resultfixture/AcronymPropertyMapperImpl.java"));
        assertTrue(generated.contains("mapped.setURL("), generated);
    }

    @Test
    void rejectsDuplicateResultProperties() throws Exception {
        Compilation compilation = compileSource("DuplicateMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface DuplicateMapper {
                @io.github.kervix.annotation.Select("SELECT id, other_id FROM users")
                @io.github.kervix.annotation.Results({
                    @io.github.kervix.annotation.Result(property = "id", column = "id"),
                    @io.github.kervix.annotation.Result(property = "id", column = "other_id")
                })
                User find();

                record User(Long id) {}
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains("duplicate @Result property 'id'"),
            compilation::diagnosticsText);
    }

    @Test
    void rejectsUnknownResultProperties() throws Exception {
        Compilation compilation = compileSource("UnknownPropertyMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface UnknownPropertyMapper {
                @io.github.kervix.annotation.Select("SELECT id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(property = "missing", column = "id"))
                User find();

                record User(Long id) {}
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "@Result property 'missing' does not exist"), compilation::diagnosticsText);
    }

    @Test
    void rejectsAnExplicitJavaTypeThatDoesNotMatchTheTarget() throws Exception {
        Compilation compilation = compileSource("JavaTypeMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface JavaTypeMapper {
                @io.github.kervix.annotation.Select("SELECT id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(
                        property = "id", column = "id", javaType = String.class))
                User find();

                record User(Long id) {}
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "declares Java type java.lang.String but the target type is java.lang.Long"),
            compilation::diagnosticsText);
    }

    @Test
    void rejectsABlankPropertyForAnObjectResult() throws Exception {
        Compilation compilation = compileSource("BlankPropertyMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface BlankPropertyMapper {
                @io.github.kervix.annotation.Select("SELECT id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(column = "id"))
                User find();

                record User(Long id) {}
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "@Result property must not be blank for an object result"), compilation::diagnosticsText);
    }

    @Test
    void rejectsAnExplicitScalarJavaTypeThatDoesNotMatchTheReturnType() throws Exception {
        Compilation compilation = compileSource("ScalarJavaTypeMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface ScalarJavaTypeMapper {
                @io.github.kervix.annotation.Select("SELECT count(*) AS total FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(column = "total", javaType = String.class))
                long count();
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "declares Java type java.lang.String but the target type is long"),
            compilation::diagnosticsText);
    }

    @Test
    void rejectsResultsOnWriteMethods() throws Exception {
        Compilation compilation = compileSource("WriteMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface WriteMapper {
                @io.github.kervix.annotation.Update("UPDATE users SET name = #{name}")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(column = "affected"))
                int update(String name);
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "@Results requires a SELECT method"), compilation::diagnosticsText);
    }

    @Test
    void rejectsResultsWhenTheSelectMethodHasNoResultType() throws Exception {
        Compilation compilation = compileSource("VoidSelectMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface VoidSelectMapper {
                @io.github.kervix.annotation.Select("SELECT id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(column = "id"))
                void find();
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "@Results requires a mapped result type"), compilation::diagnosticsText);
    }

    @Test
    void rejectsResultsCombinedWithUseRowMapper() throws Exception {
        Compilation compilation = compileSource("CustomRowMapper", """
            package io.github.kervix.test.resultfixture;

            @io.github.kervix.annotation.Mapper
            public interface CustomRowMapper {
                @io.github.kervix.annotation.Select("SELECT id FROM users")
                @io.github.kervix.annotation.Results(
                    @io.github.kervix.annotation.Result(column = "id"))
                @io.github.kervix.annotation.UseRowMapper(IdRowMapper.class)
                Long find();

                final class IdRowMapper implements io.github.kervix.api.RowMapper<Long> {
                    public Long map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
                        return resultSet.getLong(1);
                    }
                }
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "@Results cannot be combined with @UseRowMapper"), compilation::diagnosticsText);
    }

    private Compilation compileFixture() throws Exception {
        return compileSource("ResultMapper", """
            package io.github.kervix.test.resultfixture;

            import io.github.kervix.annotation.Mapper;
            import io.github.kervix.annotation.Result;
            import io.github.kervix.annotation.Results;
            import io.github.kervix.annotation.Select;

            @Mapper
            public interface ResultMapper {
                @Select("SELECT name AS user_name, id AS user_id FROM users")
                @Results({
                    @Result(property = "id", column = "user_id"),
                    @Result(property = "name", column = "user_name")
                })
                UserRecord findRecord();

                @Select("SELECT name AS user_name, id AS user_id FROM users")
                @Results({
                    @Result(property = "id", column = "user_id"),
                    @Result(property = "name", column = "user_name")
                })
                UserBean findBean();

                record UserRecord(Long id, String name) {}

                class UserBean {
                    private Long id;
                    private String name;

                    public UserBean() {}
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
            }
            """);
    }

    private Compilation compileSource(String simpleName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve("sources");
        Path classes = temporaryDirectory.resolve("classes");
        Path generated = temporaryDirectory.resolve("generated");
        Path sourceFile = sources.resolve("org/kervix/test/resultfixture/" + simpleName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                io.github.kervix.test.MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new KervixProcessor()));
            succeeded = task.call();
        }
        return new Compilation(succeeded, classes, generated, diagnostics.getDiagnostics());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> invocation.invoke(
                method.getName(), arguments == null ? new Object[0] : arguments));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws SQLException;
    }

    private record Compilation(
            boolean succeeded,
            Path classesDirectory,
            Path generatedDirectory,
            List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}

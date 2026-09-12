package org.liteorm.test.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.SqlExecutor;
import org.liteorm.compile.LiteOrmProcessor;
import org.liteorm.jdbc.JdbcSqlExecutor;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlResultMappingTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesFlatScalarRecordAndJavaBeanResultMaps() throws Exception {
        Path source = temporaryDirectory.resolve(
            "sources/org/liteorm/test/xmlresult/FlatResultMapper.java");
        Path classes = temporaryDirectory.resolve("classes");
        Path generated = temporaryDirectory.resolve("generated");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(source, """
            package org.liteorm.test.xmlresult;

            @org.liteorm.annotation.Mapper
            public interface FlatResultMapper {
                UserRecord findRecord();
                UserBean findBean();
                Long count();

                record UserRecord(Long id, String name) {}

                class UserBean {
                    private Long identifier;
                    private String displayName;

                    public UserBean() {}
                    public String getName() { return displayName; }
                    public void setName(String name) { this.displayName = name; }
                    public Long getId() { return identifier; }
                    public void setId(Long id) { this.identifier = id; }
                }
            }
            """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                org.liteorm.test.MapperCompilationTestSupport.compilationUnits(
                    List.of(source)));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }

        String diagnosticsText = diagnostics.getDiagnostics().toString();
        assertTrue(succeeded, diagnosticsText);
        String generatedSource = Files.readString(generated.resolve(
            "org/liteorm/test/xmlresult/FlatResultMapperImpl.java"));
        assertTrue(generatedSource.contains("new org.liteorm.test.xmlresult.FlatResultMapper.UserRecord("),
            generatedSource);
        assertTrue(generatedSource.contains("mapped.setId("), generatedSource);
        assertTrue(generatedSource.contains("new String[]{\"user_id\", \"user_name\"}"),
            generatedSource);
        assertTrue(generatedSource.contains("new String[]{\"total\"}"), generatedSource);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.xmlresult.FlatResultMapper");
            Class<?> implementationType = loader.loadClass(
                "org.liteorm.test.xmlresult.FlatResultMapperImpl");
            Object mapper = implementationType.getConstructor(SqlExecutor.class)
                .newInstance(jdbcExecutor());

            Object record = mapperType.getMethod("findRecord").invoke(mapper);
            Object bean = mapperType.getMethod("findBean").invoke(mapper);
            Object count = mapperType.getMethod("count").invoke(mapper);

            assertEquals(7L, record.getClass().getMethod("id").invoke(record));
            assertEquals("Alice", record.getClass().getMethod("name").invoke(record));
            assertEquals(7L, bean.getClass().getMethod("getId").invoke(bean));
            assertEquals("Alice", bean.getClass().getMethod("getName").invoke(bean));
            assertEquals(2L, count);
        }
    }

    @Test
    void rejectsXmlAndAnnotationResultMappingOnTheSameMethod() throws Exception {
        Compilation compilation = compile("ConflictResultMapper", """
            package org.liteorm.test.xmlresult;

            @org.liteorm.annotation.Mapper
            public interface ConflictResultMapper {
                @org.liteorm.annotation.Results(
                    @org.liteorm.annotation.Result(column = "value"))
                String find();
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains(
            "XML resultMap cannot be combined with @Results"), compilation::diagnosticsText);
    }

    @Test
    void rejectsCollectionAndDiscriminatorMappings() throws Exception {
        Compilation collection = compileValueMapper("CollectionResultMapper");
        assertFalse(collection.succeeded(), collection::diagnosticsText);
        assertTrue(collection.diagnosticsText().contains(
            "Unsupported XML <collection> in resultMap 'valueResult'"), collection::diagnosticsText);

        Compilation discriminator = compileValueMapper("DiscriminatorResultMapper");
        assertFalse(discriminator.succeeded(), discriminator::diagnosticsText);
        assertTrue(discriminator.diagnosticsText().contains(
            "Unsupported XML <discriminator> in resultMap 'valueResult'"),
            discriminator::diagnosticsText);
    }

    @Test
    void rejectsNestedSelectAndLazyLoadingAttributes() throws Exception {
        Compilation nestedSelect = compileValueMapper("NestedSelectResultMapper");
        assertFalse(nestedSelect.succeeded(), nestedSelect::diagnosticsText);
        assertTrue(nestedSelect.diagnosticsText().contains(
            "Unsupported XML attribute 'select' on <result>"), nestedSelect::diagnosticsText);

        Compilation lazy = compileValueMapper("LazyResultMapper");
        assertFalse(lazy.succeeded(), lazy::diagnosticsText);
        assertTrue(lazy.diagnosticsText().contains(
            "Unsupported XML attribute 'fetchType' on <result>"), lazy::diagnosticsText);
    }

    @Test
    void reportsTheXmlPathForInvalidResultMapIdentifiers() throws Exception {
        Compilation blank = compileValueMapper("BlankResultMapIdMapper");
        assertFalse(blank.succeeded(), blank::diagnosticsText);
        assertTrue(blank.diagnosticsText().contains(
            "XML resource /org/liteorm/test/xmlresult/BlankResultMapIdMapper.xml"),
            blank::diagnosticsText);
        assertTrue(blank.diagnosticsText().contains(
            "<resultMap> requires non-blank attribute 'id'"), blank::diagnosticsText);

        Compilation duplicate = compileValueMapper("DuplicateResultMapIdMapper");
        assertFalse(duplicate.succeeded(), duplicate::diagnosticsText);
        assertTrue(duplicate.diagnosticsText().contains(
            "XML resource /org/liteorm/test/xmlresult/DuplicateResultMapIdMapper.xml"),
            duplicate::diagnosticsText);
        assertTrue(duplicate.diagnosticsText().contains(
            "duplicate <resultMap> id 'valueResult'"), duplicate::diagnosticsText);
    }

    private Compilation compileValueMapper(String mapperName) throws Exception {
        return compile(mapperName, """
            package org.liteorm.test.xmlresult;

            @org.liteorm.annotation.Mapper
            public interface %s {
                Value find();

                class Value {
                    private String value;

                    public Value() {}
                    public String getValue() { return value; }
                    public void setValue(String value) { this.value = value; }
                }
            }
            """.formatted(mapperName));
    }

    private Compilation compile(String mapperName, String sourceText) throws Exception {
        Path source = temporaryDirectory.resolve(
            "invalid-sources/org/liteorm/test/xmlresult/" + mapperName + ".java");
        Path classes = temporaryDirectory.resolve("invalid-classes-" + mapperName);
        Path generated = temporaryDirectory.resolve("invalid-generated-" + mapperName);
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(source, sourceText, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                org.liteorm.test.MapperCompilationTestSupport.compilationUnits(
                    List.of(source)));
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

    private SqlExecutor jdbcExecutor() {
        Connection connection = proxy(Connection.class, (method, arguments) -> {
            if (!method.equals("prepareStatement")) {
                return null;
            }
            boolean countQuery = ((String) arguments[0]).contains("count(*)");
            ResultSet resultSet = resultSet(countQuery);
            return proxy(PreparedStatement.class, (statementMethod, statementArguments) ->
                statementMethod.equals("executeQuery") ? resultSet : null);
        });
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

    private ResultSet resultSet(boolean countQuery) {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> {
            int columnIndex = arguments.length == 0 ? -1 : (int) arguments[0];
            return switch (method) {
                case "getColumnCount" -> countQuery ? 1 : 2;
                case "getColumnLabel", "getColumnName" -> countQuery
                    ? "total" : columnIndex == 1 ? "user_id" : "user_name";
                case "getColumnType" -> countQuery || columnIndex == 1
                    ? JDBCType.BIGINT.getVendorTypeNumber() : JDBCType.VARCHAR.getVendorTypeNumber();
                case "getColumnTypeName" -> countQuery || columnIndex == 1 ? "BIGINT" : "VARCHAR";
                default -> null;
            };
        });
        int[] row = {-1};
        return proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> ++row[0] == 0;
            case "getString" -> "Alice";
            case "getObject" -> countQuery ? 2L : (int) arguments[0] == 1 ? 7L : "Alice";
            default -> null;
        });
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
            List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        private String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}

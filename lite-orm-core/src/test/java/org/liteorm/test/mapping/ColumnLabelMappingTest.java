package org.liteorm.test.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.api.ResultColumn;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlResult;
import org.liteorm.compile.LiteOrmProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnLabelMappingTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsRecordsAndJavaBeansByColumnLabelInsteadOfSelectOrder() throws Exception {
        Compilation compilation = compileFixture();
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        String generated = Files.readString(compilation.generatedDirectory().resolve(
            "org/liteorm/test/columnfixture/LabelMapperImpl.java"));
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"id\")"), generated);
        assertTrue(generated.contains("executionResult.requireColumnIndex(\"name\")"), generated);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{compilation.classesDirectory().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapper");
            Class<?> implementationType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapperImpl");
            SqlExecutor executor = plan -> SqlResult.forQuery(
                List.of(new ResultColumn("name", 0), new ResultColumn("id", 1)),
                List.<Object[]>of(new Object[]{"Alice", 7L}));
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            Object record = mapperType.getMethod("findRecord").invoke(mapper);
            Object bean = mapperType.getMethod("findBean").invoke(mapper);

            assertEquals(7L, record.getClass().getMethod("id").invoke(record));
            assertEquals("Alice", record.getClass().getMethod("name").invoke(record));
            assertEquals(7L, bean.getClass().getMethod("getId").invoke(bean));
            assertEquals("Alice", bean.getClass().getMethod("getName").invoke(bean));
        }
    }

    @Test
    void failsClearlyWhenRequiredColumnIsMissing() throws Exception {
        Compilation compilation = compileFixture();
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{compilation.classesDirectory().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapper");
            Class<?> implementationType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapperImpl");
            SqlExecutor executor = plan -> SqlResult.forQuery(
                List.of(new ResultColumn("name", 0)),
                List.<Object[]>of(new Object[]{"Alice"}));
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            InvocationTargetException failure = assertThrows(
                InvocationTargetException.class, () -> mapperType.getMethod("findRecord").invoke(mapper));

            assertTrue(failure.getCause().getMessage().contains("id"), failure.getCause()::getMessage);
        }
    }

    @Test
    void emptyResultsDoNotRequireColumnMetadata() throws Exception {
        Compilation compilation = compileFixture();
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{compilation.classesDirectory().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapper");
            Class<?> implementationType = loader.loadClass("org.liteorm.test.columnfixture.LabelMapperImpl");
            SqlExecutor executor = plan -> SqlResult.forQuery(List.of());
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            assertNull(mapperType.getMethod("findRecord").invoke(mapper));
            assertTrue(((List<?>) mapperType.getMethod("findRecords").invoke(mapper)).isEmpty());
        }
    }

    private Compilation compileFixture() throws Exception {
        Path sources = temporaryDirectory.resolve("sources");
        Path classes = temporaryDirectory.resolve("classes");
        Path generated = temporaryDirectory.resolve("generated");
        Path sourceFile = sources.resolve("org/liteorm/test/columnfixture/LabelMapper.java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(generated);
        Files.writeString(sourceFile, """
            package org.liteorm.test.columnfixture;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface LabelMapper {
                @Select("SELECT display_name AS name, id FROM users")
                UserRecord findRecord();

                @Select("SELECT display_name AS name, id FROM users")
                java.util.List<UserRecord> findRecords();

                @Select("SELECT display_name AS name, id FROM users")
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
            """, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        boolean succeeded;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            var units = manager.getJavaFileObjectsFromPaths(
                org.liteorm.test.MapperCompilationTestSupport.withJdbcTypeMappingsSelection(List.of(sourceFile)));
            var task = compiler.getTask(null, manager, diagnostics, List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(),
                "-s", generated.toString()
            ), null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }
        return new Compilation(succeeded, classes, generated, diagnostics.getDiagnostics());
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

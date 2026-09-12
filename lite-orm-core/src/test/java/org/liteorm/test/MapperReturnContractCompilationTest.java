package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.api.MappingException;
import org.liteorm.api.NonUniqueResultException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperReturnContractCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void supportsReferenceOptionalPrimitiveListAndSingleResultContracts() throws Exception {
        Compilation compilation = compile("ReturnMapper", """
            package org.liteorm.test.returnfixture;

            import java.util.List;
            import java.util.Optional;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface ReturnMapper {
                @Select("SELECT value FROM values_table") String findReference();
                @Select("SELECT value FROM values_table") Optional<String> findOptionalEmpty();
                @Select("SELECT value FROM values_table") Optional<String> findOptionalPresent();
                @Select("SELECT value FROM values_table") long findPrimitive();
                @Select("SELECT value FROM values_table") List<String> findList();
                @Select("SELECT value FROM values_table") String findMultiple();
            }
            """);
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.returnfixture.ReturnMapper");
            Class<?> implementationType = loader.loadClass("org.liteorm.test.returnfixture.ReturnMapperImpl");
            SqlExecutor executor = plan -> switch (plan.getStatementId()) {
                case "org.liteorm.test.returnfixture.ReturnMapper.findOptionalPresent" ->
                    SqlResult.forQuery(List.<Object[]>of(new Object[]{"Alice"}));
                case "org.liteorm.test.returnfixture.ReturnMapper.findList" ->
                    SqlResult.forQuery(List.of(new Object[]{"Alice"}, new Object[]{"Bob"}));
                case "org.liteorm.test.returnfixture.ReturnMapper.findMultiple" ->
                    SqlResult.forQuery(List.of(new Object[]{"Alice"}, new Object[]{"Bob"}));
                default -> SqlResult.forQuery(List.of());
            };
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            assertNull(mapperType.getMethod("findReference").invoke(mapper));
            assertEquals(Optional.empty(), mapperType.getMethod("findOptionalEmpty").invoke(mapper));
            assertEquals(Optional.of("Alice"), mapperType.getMethod("findOptionalPresent").invoke(mapper));
            assertEquals(List.of("Alice", "Bob"), mapperType.getMethod("findList").invoke(mapper));

            InvocationTargetException primitiveFailure = assertThrows(
                InvocationTargetException.class, () -> mapperType.getMethod("findPrimitive").invoke(mapper));
            MappingException mappingFailure = assertInstanceOf(MappingException.class, primitiveFailure.getCause());
            assertTrue(mappingFailure.getMessage().contains("ReturnMapper.findPrimitive"));

            InvocationTargetException multipleFailure = assertThrows(
                InvocationTargetException.class, () -> mapperType.getMethod("findMultiple").invoke(mapper));
            assertInstanceOf(NonUniqueResultException.class, multipleFailure.getCause());
        }
    }

    @Test
    void characterizesCustomExecutorResultsAcrossExecutionShapes() throws Exception {
        Compilation compilation = compile("ExecutionShapeMapper", """
            package org.liteorm.test.returnfixture;

            import java.util.List;
            import org.liteorm.annotation.Batch;
            import org.liteorm.annotation.GeneratedKey;
            import org.liteorm.annotation.Insert;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;
            import org.liteorm.annotation.Update;

            @Mapper
            public interface ExecutionShapeMapper {
                @Select("SELECT value FROM values_table") String findEmpty();
                @Select("SELECT value FROM values_table") String findSingle();
                @Select("SELECT value FROM values_table") List<String> findMultiple();
                @Update("UPDATE values_table SET value = #{value}") int update(String value);
                @GeneratedKey("id")
                @Insert("INSERT INTO values_table(value) VALUES (#{value})")
                long insert(String value);
                @Batch("INSERT INTO values_table(value) VALUES (#{item})")
                int[] insertBatch(List<String> values);
            }
            """);
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.returnfixture.ExecutionShapeMapper");
            Class<?> implementationType =
                loader.loadClass("org.liteorm.test.returnfixture.ExecutionShapeMapperImpl");
            SqlExecutor executor = plan -> switch (plan.getStatementId()) {
                case "org.liteorm.test.returnfixture.ExecutionShapeMapper.findSingle" ->
                    SqlResult.forQuery(List.<Object[]>of(new Object[]{"Alice"}));
                case "org.liteorm.test.returnfixture.ExecutionShapeMapper.findMultiple" ->
                    SqlResult.forQuery(List.of(new Object[]{"Alice"}, new Object[]{"Bob"}));
                case "org.liteorm.test.returnfixture.ExecutionShapeMapper.update" ->
                    SqlResult.forUpdate(3);
                case "org.liteorm.test.returnfixture.ExecutionShapeMapper.insert" ->
                    SqlResult.forGeneratedKey(1, 42L);
                case "org.liteorm.test.returnfixture.ExecutionShapeMapper.insertBatch" -> {
                    org.liteorm.api.BatchExecutionPlan batchPlan =
                        (org.liteorm.api.BatchExecutionPlan) plan;
                    yield SqlResult.forBatch(batchPlan.getBatchParameters().isEmpty()
                        ? new int[0] : new int[]{1, 1});
                }
                default -> SqlResult.forQuery(List.of());
            };
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            assertNull(invoke(mapperType, mapper, "findEmpty"));
            assertEquals("Alice", invoke(mapperType, mapper, "findSingle"));
            assertEquals(List.of("Alice", "Bob"), invoke(mapperType, mapper, "findMultiple"));
            assertEquals(3, invoke(mapperType, mapper, "update", String.class, "Alice"));
            assertEquals(42L, invoke(mapperType, mapper, "insert", String.class, "Alice"));
            assertArrayEquals(new int[0], (int[]) invoke(
                mapperType, mapper, "insertBatch", List.class, List.of()));
            assertArrayEquals(new int[]{1, 1}, (int[]) invoke(
                mapperType, mapper, "insertBatch", List.class, List.of("Alice", "Bob")));
        }
    }

    @Test
    void propagatesCustomExecutorFailuresForEveryExecutionShape() throws Exception {
        Compilation compilation = compile("FailingExecutionShapeMapper", """
            package org.liteorm.test.returnfixture;

            import java.util.List;
            import org.liteorm.annotation.Batch;
            import org.liteorm.annotation.GeneratedKey;
            import org.liteorm.annotation.Insert;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;
            import org.liteorm.annotation.Update;

            @Mapper
            public interface FailingExecutionShapeMapper {
                @Select("SELECT value FROM values_table") String find();
                @Update("UPDATE values_table SET value = #{value}") int update(String value);
                @GeneratedKey("id")
                @Insert("INSERT INTO values_table(value) VALUES (#{value})")
                long insert(String value);
                @Batch("INSERT INTO values_table(value) VALUES (#{item})")
                int[] insertBatch(List<String> values);
            }
            """);
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass(
                "org.liteorm.test.returnfixture.FailingExecutionShapeMapper");
            Class<?> implementationType = loader.loadClass(
                "org.liteorm.test.returnfixture.FailingExecutionShapeMapperImpl");
            RuntimeException failure = new IllegalStateException("custom executor failure");
            SqlExecutor executor = plan -> {
                throw failure;
            };
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            assertSameFailure(failure, () -> invoke(mapperType, mapper, "find"));
            assertSameFailure(failure, () -> invoke(
                mapperType, mapper, "update", String.class, "Alice"));
            assertSameFailure(failure, () -> invoke(
                mapperType, mapper, "insert", String.class, "Alice"));
            assertSameFailure(failure, () -> invoke(
                mapperType, mapper, "insertBatch", List.class, List.of("Alice")));
        }
    }

    @Test
    void preservesDefaultMethodsAndGeneratesResolvedInheritedMethodsOnce() throws Exception {
        Compilation compilation = compile("InheritedMapper", """
            package org.liteorm.test.returnfixture;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            interface GenericMapper<T> {
                @Select("SELECT #{value}") T findInherited(T value);

                default String localDefault() {
                    return "default";
                }

                @Select("SELECT 'sql'")
                default String annotatedDefault() {
                    return "fallback";
                }
            }

            @Mapper
            public interface InheritedMapper extends GenericMapper<String> {
            }
            """);
        assertTrue(compilation.succeeded(), compilation::diagnosticsText);

        String generatedSource = Files.readString(compilation.generatedDirectory()
            .resolve("org/liteorm/test/returnfixture/InheritedMapperImpl.java"));
        assertEquals(1, occurrences(generatedSource, "findInherited("));
        assertEquals(0, occurrences(generatedSource, "localDefault("));
        assertEquals(1, occurrences(generatedSource, "annotatedDefault("));
        assertTrue(generatedSource.contains(
            "java.lang.String findInherited(java.lang.String value)"), generatedSource);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperType = loader.loadClass("org.liteorm.test.returnfixture.InheritedMapper");
            Class<?> implementationType = loader.loadClass("org.liteorm.test.returnfixture.InheritedMapperImpl");
            SqlExecutor executor = plan -> switch (plan.getStatementId()) {
                case "org.liteorm.test.returnfixture.InheritedMapper.findInherited" ->
                    SqlResult.forQuery(List.<Object[]>of(new Object[]{plan.getParameters()[0]}));
                case "org.liteorm.test.returnfixture.InheritedMapper.annotatedDefault" ->
                    SqlResult.forQuery(List.<Object[]>of(new Object[]{"sql"}));
                default -> throw new AssertionError(plan.getStatementId());
            };
            Object mapper = implementationType.getConstructor(SqlExecutor.class).newInstance(executor);

            assertEquals("default", invoke(mapperType, mapper, "localDefault"));
            assertEquals("sql", invoke(implementationType, mapper, "annotatedDefault"));
            assertEquals("Alice", invoke(
                implementationType, mapper, "findInherited", String.class, "Alice"));
        }
    }

    @Test
    void rejectsUnresolvedGenericReturnShapesWithMapperMethodDiagnostic() throws Exception {
        Compilation compilation = compile("UnresolvedMapper", """
            package org.liteorm.test.returnfixture;

            import java.util.Optional;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface UnresolvedMapper<T> {
                @Select("SELECT value FROM values_table") Optional<T> findValue();
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains("UnresolvedMapper#findValue"), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains("unresolved generic type T"), compilation::diagnosticsText);
    }

    @Test
    void rejectsWildcardReturnShapesWithMapperMethodDiagnostic() throws Exception {
        Compilation compilation = compile("WildcardMapper", """
            package org.liteorm.test.returnfixture;

            import java.util.Optional;
            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface WildcardMapper {
                @Select("SELECT value FROM values_table") Optional<?> findValue();
            }
            """);

        assertFalse(compilation.succeeded(), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains("WildcardMapper#findValue"), compilation::diagnosticsText);
        assertTrue(compilation.diagnosticsText().contains("unresolved generic type ?"), compilation::diagnosticsText);
    }

    private int occurrences(String source, String fragment) {
        return source.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private Object invoke(Class<?> mapperType, Object mapper, String methodName, Object... parameterTypeAndValue)
            throws Exception {
        int parameterCount = parameterTypeAndValue.length / 2;
        Class<?>[] parameterTypes = new Class<?>[parameterCount];
        Object[] arguments = new Object[parameterCount];
        for (int index = 0; index < parameterCount; index++) {
            parameterTypes[index] = (Class<?>) parameterTypeAndValue[index * 2];
            arguments[index] = parameterTypeAndValue[index * 2 + 1];
        }
        var method = mapperType.getMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(mapper, arguments);
    }

    private void assertSameFailure(RuntimeException expected, ThrowingInvocation invocation) {
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, invocation::invoke);
        assertSame(expected, thrown.getCause());
    }

    @FunctionalInterface
    private interface ThrowingInvocation {
        void invoke() throws Exception;
    }

    private Compilation compile(String typeName, String source) throws Exception {
        Path sources = temporaryDirectory.resolve(typeName + "/sources");
        Path classes = temporaryDirectory.resolve(typeName + "/classes");
        Path generated = temporaryDirectory.resolve(typeName + "/generated");
        Path sourceFile = sources.resolve("org/liteorm/test/returnfixture/" + typeName + ".java");
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
                MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
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

        URLClassLoader classLoader(ClassLoader parent) throws Exception {
            return new URLClassLoader(new java.net.URL[]{classesDirectory.toUri().toURL()}, parent);
        }

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}

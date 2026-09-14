package io.github.kervix.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.kervix.compile.KervixProcessor;

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

class BatchCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesStaticBatchParameterLoop() throws Exception {
        CompilationResult result = compile("BatchMapper", """
            package io.github.kervix.test.batchfixture;

            import java.util.List;
            import io.github.kervix.annotation.Batch;
            import io.github.kervix.annotation.Mapper;

            record User(Long id, String name) {}

            @Mapper
            interface BatchMapper {
                @Batch("INSERT INTO users (id, name) VALUES (#{item.id}, #{item.name})")
                int[] insertAll(List<User> users);
            }
            """);

        assertTrue(result.succeeded(), result::diagnosticsText);
        String generated = Files.readString(result.generatedDirectory().resolve(
            "org/kervix/test/batchfixture/BatchMapperImpl.java"));
        assertTrue(generated.contains("for (io.github.kervix.test.batchfixture.User item : users)"));
        assertTrue(generated.contains("params[0] = item.id();"));
        assertTrue(generated.contains("params[1] = item.name();"));
        assertTrue(generated.contains("BatchDefinition INSERT_ALL_DEFINITION"));
        assertTrue(generated.contains("return INSERT_ALL_DEFINITION.bind(batchParameters);"));
        assertFalse(generated.contains("return new BatchExecutionPlan"));
    }

    @Test
    void rejectsBatchMethodsWithoutOneListParameterAndIntArrayReturn() throws Exception {
        CompilationResult result = compile("InvalidBatchMapper", """
            package io.github.kervix.test.batchfixture;

            import io.github.kervix.annotation.Batch;
            import io.github.kervix.annotation.Mapper;

            @Mapper
            interface InvalidBatchMapper {
                @Batch("INSERT INTO users (id) VALUES (#{item})")
                int insertAll(Long id);
            }
            """);

        assertFalse(result.succeeded());
        assertTrue(result.diagnosticsText().contains(
            "batch methods require exactly one java.util.List<T> parameter"));
    }

    private CompilationResult compile(String typeName, String source) throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve(typeName + "-sources");
        Path classesDirectory = temporaryDirectory.resolve(typeName + "-classes");
        Path generatedDirectory = temporaryDirectory.resolve(typeName + "-generated");
        Path sourceFile = sourceDirectory.resolve("org/kervix/test/batchfixture/" + typeName + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean succeeded;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(sourceFile)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new KervixProcessor()));
            succeeded = task.call();
        }
        return new CompilationResult(succeeded, generatedDirectory, diagnostics.getDiagnostics());
    }

    private record CompilationResult(
        boolean succeeded,
        Path generatedDirectory,
        List<Diagnostic<? extends JavaFileObject>> diagnostics) {

        String diagnosticsText() {
            return diagnostics.toString();
        }
    }
}

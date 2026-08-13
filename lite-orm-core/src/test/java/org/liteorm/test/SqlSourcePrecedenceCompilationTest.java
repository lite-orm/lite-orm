package org.liteorm.test;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSourcePrecedenceCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void xmlOverridesAnnotationAndEmitsCompilerWarning() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve("org/liteorm/test/precedence/ConflictingMapper.java");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package org.liteorm.test.precedence;

            import org.apache.ibatis.annotations.Mapper;
            import org.apache.ibatis.annotations.Select;

            record ValueRow(String value) {
            }

            @Mapper
            public interface ConflictingMapper {
                @Select("SELECT 'annotation-source'")
                ValueRow findValue();

                @Select("SELECT 'annotation-only'")
                ValueRow findAnnotationOnly();
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromPaths(List.of(mapperSource));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);
            task.setProcessors(List.of(new LiteOrmProcessor()));

            assertTrue(task.call(), () -> diagnostics.getDiagnostics().toString());
        }

        String generatedSource = Files.readString(
            generatedDirectory.resolve("org/liteorm/test/precedence/ConflictingMapperImpl.java"));
        assertTrue(generatedSource.contains("SELECT 'xml-source'"), generatedSource);
        assertTrue(generatedSource.contains("ExecutionPlan.SqlSource.XML"), generatedSource);

        assertTrue(diagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.getKind() == Diagnostic.Kind.WARNING
                    && diagnostic.getMessage(null).contains("XML SQL overrides annotation SQL")
                    && diagnostic.getMessage(null).contains("ConflictingMapper#findValue")),
            () -> diagnostics.getDiagnostics().toString());
        assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic ->
                diagnostic.getKind() == Diagnostic.Kind.WARNING
                    && diagnostic.getMessage(null).contains("ConflictingMapper#findAnnotationOnly")),
            () -> diagnostics.getDiagnostics().toString());
    }
}

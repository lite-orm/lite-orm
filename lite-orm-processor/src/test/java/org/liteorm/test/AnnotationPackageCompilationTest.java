package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.liteorm.compile.LiteOrmProcessor;

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

class AnnotationPackageCompilationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapperCompilesUsingOnlyLiteOrmAnnotations() throws Exception {
        Path sourceDirectory = temporaryDirectory.resolve("sources");
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path generatedDirectory = temporaryDirectory.resolve("generated");
        Path mapperSource = sourceDirectory.resolve("org/liteorm/test/annotationfixture/LiteMapper.java");

        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package org.liteorm.test.annotationfixture;

            import org.liteorm.annotation.Mapper;
            import org.liteorm.annotation.Param;
            import org.liteorm.annotation.Select;

            @Mapper
            public interface LiteMapper {
                @Select("SELECT #{value}")
                Long find(@Param("value") Long value);
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean succeeded;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(mapperSource)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path"),
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LiteOrmProcessor()));
            succeeded = task.call();
        }

        assertTrue(succeeded, () -> diagnostics.getDiagnostics().toString());
        assertTrue(Files.exists(generatedDirectory.resolve(
            "org/liteorm/test/annotationfixture/LiteMapperImpl.java")));
    }

    @Test
    void sourceTreeDoesNotContainLegacyIbatisAnnotationPackage() throws Exception {
        Path sourceRoot = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.exists(sourceRoot)) {
            sourceRoot = Path.of(System.getProperty("user.dir"), "lite-orm-core", "src", "main", "java");
        }

        assertFalse(Files.exists(sourceRoot.resolve("org/apache/ibatis/annotations")));
    }
}

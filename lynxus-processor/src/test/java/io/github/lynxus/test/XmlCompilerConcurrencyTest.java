package io.github.lynxus.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.lynxus.compile.LynxusProcessor;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlCompilerConcurrencyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parallelCompilationsIsolateIdenticallyNamedXmlResources() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var alpha = executor.submit(() -> compile("alpha", ready, start));
            var beta = executor.submit(() -> compile("beta", ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            CompilationResult alphaResult = alpha.get(10, TimeUnit.SECONDS);
            CompilationResult betaResult = beta.get(10, TimeUnit.SECONDS);
            assertTrue(alphaResult.success(), alphaResult.diagnostics());
            assertTrue(betaResult.success(), betaResult.diagnostics());
            assertTrue(alphaResult.generatedSource().contains("SELECT 'alpha'"), alphaResult.generatedSource());
            assertFalse(alphaResult.generatedSource().contains("SELECT 'beta'"), alphaResult.generatedSource());
            assertTrue(betaResult.generatedSource().contains("SELECT 'beta'"), betaResult.generatedSource());
            assertFalse(betaResult.generatedSource().contains("SELECT 'alpha'"), betaResult.generatedSource());
        }
    }

    private CompilationResult compile(String value, CountDownLatch ready, CountDownLatch start) throws Exception {
        Path compilationRoot = temporaryDirectory.resolve(value);
        Path sourceDirectory = compilationRoot.resolve("sources");
        Path resourceDirectory = compilationRoot.resolve("resources");
        Path classesDirectory = compilationRoot.resolve("classes");
        Path generatedDirectory = compilationRoot.resolve("generated");
        Path mapperSource = sourceDirectory.resolve("isolated/SharedMapper.java");
        Path mapperXml = resourceDirectory.resolve("isolated/SharedMapper.xml");
        Files.createDirectories(mapperSource.getParent());
        Files.createDirectories(mapperXml.getParent());
        Files.createDirectories(classesDirectory);
        Files.createDirectories(generatedDirectory);
        Files.writeString(mapperSource, """
            package isolated;

            @io.github.lynxus.annotation.Mapper
            public interface SharedMapper {
                String value();
            }
            """, StandardCharsets.UTF_8);
        Files.writeString(mapperXml, """
            <?xml version="1.0" encoding="UTF-8"?>
            <mapper namespace="isolated.SharedMapper">
                <sql id="selectedValue">SELECT '%s'</sql>
                <select id="value" resultType="java.lang.String">
                    <include refid="selectedValue"/>
                </select>
            </mapper>
            """.formatted(value), StandardCharsets.UTF_8);

        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting to start parallel compilation");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
            diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(
                MapperCompilationTestSupport.compilationUnits(List.of(mapperSource)));
            List<String> options = List.of(
                "--release", "21",
                "-classpath", System.getProperty("java.class.path") + File.pathSeparator + resourceDirectory,
                "-d", classesDirectory.toString(),
                "-s", generatedDirectory.toString()
            );
            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new LynxusProcessor()));
            boolean success = task.call();
            Path generatedMapper = generatedDirectory.resolve("isolated/SharedMapperImpl.java");
            String generatedSource = Files.exists(generatedMapper) ? Files.readString(generatedMapper) : "";
            return new CompilationResult(success, diagnostics.getDiagnostics().toString(), generatedSource);
        }
    }

    private record CompilationResult(boolean success, String diagnostics, String generatedSource) {
    }
}

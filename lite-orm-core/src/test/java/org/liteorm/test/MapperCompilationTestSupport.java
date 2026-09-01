package org.liteorm.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapperCompilationTestSupport {

    private static final Pattern PACKAGE_DECLARATION =
        Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;");

    private MapperCompilationTestSupport() {
    }

    public static List<Path> withJdbcTypeMappingsSelection(List<Path> sourceFiles) throws IOException {
        List<Path> compilationUnits = new ArrayList<>(sourceFiles);
        Map<Path, String> packages = new LinkedHashMap<>();
        for (Path sourceFile : sourceFiles) {
            if (sourceFile.getFileName().toString().equals("package-info.java")) {
                continue;
            }
            Matcher matcher = PACKAGE_DECLARATION.matcher(Files.readString(sourceFile));
            if (matcher.find()) {
                packages.putIfAbsent(sourceFile.getParent(), matcher.group(1));
            }
        }
        for (Map.Entry<Path, String> packageEntry : packages.entrySet()) {
            Path packageInfo = packageEntry.getKey().resolve("package-info.java");
            if (!Files.exists(packageInfo)) {
                Files.writeString(packageInfo, """
                    @org.liteorm.annotation.UseJdbcTypeMappings(org.liteorm.test.TestJdbcTypeMappings.class)
                    package %s;
                    """.formatted(packageEntry.getValue()), StandardCharsets.UTF_8);
            }
            compilationUnits.add(packageInfo);
        }
        return List.copyOf(compilationUnits);
    }
}

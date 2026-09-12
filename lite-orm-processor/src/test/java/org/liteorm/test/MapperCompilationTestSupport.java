package org.liteorm.test;

import java.nio.file.Path;
import java.util.List;

public final class MapperCompilationTestSupport {

    private MapperCompilationTestSupport() {
    }

    public static List<Path> compilationUnits(List<Path> sourceFiles) {
        return List.copyOf(sourceFiles);
    }
}

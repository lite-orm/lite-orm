package org.liteorm.compile;

import java.util.List;

/**
 * Structured source model consumed by the Mapper implementation renderer.
 *
 * <p>Compiler code owns the contents of each generated member. The renderer
 * only owns package, import, class, and member layout.</p>
 */
record GeneratedMapperSourceModel(
    String packageName,
    String interfaceName,
    String implementationName,
    List<String> imports,
    List<String> fields,
    List<String> members
) {

    GeneratedMapperSourceModel {
        imports = List.copyOf(imports);
        fields = List.copyOf(fields);
        members = List.copyOf(members);
    }
}

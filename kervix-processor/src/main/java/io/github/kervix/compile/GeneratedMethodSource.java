package io.github.kervix.compile;

/**
 * Structured declaration for a generated Mapper method.
 *
 * <p>The body remains compiler-produced Java text because it contains
 * execution and result-adaptation decisions.</p>
 */
record GeneratedMethodSource(
    String documentation,
    String returnType,
    String methodName,
    String parameters,
    String body
) {
}

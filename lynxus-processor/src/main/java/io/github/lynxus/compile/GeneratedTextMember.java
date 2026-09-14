package io.github.lynxus.compile;

/**
 * A generated member whose Java text is already semantically complete.
 */
record GeneratedTextMember(GeneratedSourceMember.Kind kind, String source)
    implements GeneratedSourceMember {
}

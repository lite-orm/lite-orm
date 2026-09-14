package io.github.kervix.compile;

/**
 * A generated Mapper method with a structured declaration and Java body.
 */
record GeneratedMethodMember(GeneratedMethodSource method)
    implements GeneratedSourceMember {

    @Override
    public Kind kind() {
        return Kind.MAPPER_METHOD;
    }
}

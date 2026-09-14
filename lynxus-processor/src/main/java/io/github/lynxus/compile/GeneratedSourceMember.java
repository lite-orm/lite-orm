package io.github.lynxus.compile;

/**
 * A compiler-produced generated-source member.
 */
sealed interface GeneratedSourceMember permits GeneratedTextMember, GeneratedMethodMember {

    Kind kind();

    enum Kind {
        DEFINITION,
        MAPPER_METHOD,
        EXECUTION_FACTORY,
        HELPER
    }
}

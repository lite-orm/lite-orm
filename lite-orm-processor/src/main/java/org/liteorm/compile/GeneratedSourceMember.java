package org.liteorm.compile;

/**
 * A compiler-produced generated-source member.
 *
 * <p>The source remains Java text because compiler code owns its semantics.
 * The kind gives the renderer and later model refinements an explicit member
 * contract without introducing a second Java compiler.</p>
 */
record GeneratedSourceMember(Kind kind, String source) {

    enum Kind {
        METHOD,
        HELPER
    }
}

package io.github.kervix.test;

/**
 * Immutable user record used as a direct generated mapping target.
 * 
 * @author kervix
 * @since 2024/09/29
 */
public record User(
    Long id,
    String name, 
    String email,
    Integer age
) {
    /**
     * Creates a user from a positional row without reflection.
     */
    public static User fromResultSet(Object[] row) {
        return new User(
            (Long) row[0],
            (String) row[1],
            (String) row[2],
            (Integer) row[3]
        );
    }
}

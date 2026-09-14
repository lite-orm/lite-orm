package io.github.kervix.api;

/**
 * Base class for public Kervix failures.
 *
 * @author kervix
 * @since 2024/11/15
 */
public class KervixException extends RuntimeException {

    public KervixException(String message) {
        super(message);
    }

    public KervixException(String message, Throwable cause) {
        super(message, cause);
    }

    public KervixException(Throwable cause) {
        super(cause);
    }
}

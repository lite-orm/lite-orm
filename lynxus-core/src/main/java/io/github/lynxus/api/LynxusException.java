package io.github.lynxus.api;

/**
 * Base class for public Lynxus failures.
 *
 * @author lynxus
 * @since 2024/11/15
 */
public class LynxusException extends RuntimeException {

    public LynxusException(String message) {
        super(message);
    }

    public LynxusException(String message, Throwable cause) {
        super(message, cause);
    }

    public LynxusException(Throwable cause) {
        super(cause);
    }
}

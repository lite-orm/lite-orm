package org.liteorm.api;

/**
 * Constructs one standard query result from values already converted by Core JDBC type routing.
 *
 * <p>Implementations are generated at compile time and invoked while the JDBC result set is still
 * owned by the executor. They must not retain the supplied {@link ResultRow}.</p>
 *
 * @param <T> mapped query result type
 */
@FunctionalInterface
public interface ResultAssembler<T> {

    T assemble(ResultRow row);
}

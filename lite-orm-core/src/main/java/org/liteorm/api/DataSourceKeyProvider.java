package org.liteorm.api;

/**
 * Selects one named SqlExecutor through an ordinary typed Java call.
 */
@FunctionalInterface
public interface DataSourceKeyProvider<P> {

    String select(DataSourceSelection<P> selection);

    /**
     * Annotation sentinel indicating that no dynamic provider is configured.
     */
    final class None implements DataSourceKeyProvider<Object> {

        private None() {
        }

        @Override
        public String select(DataSourceSelection<Object> selection) {
            throw new UnsupportedOperationException("No DataSourceKeyProvider is configured");
        }
    }
}

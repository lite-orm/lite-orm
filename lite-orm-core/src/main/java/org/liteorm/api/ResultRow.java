package org.liteorm.api;

import java.util.Objects;

/**
 * Read-only view of one standard query row in generated result-mapping order.
 *
 * <p>Instances are valid only during one {@link ResultAssembler} invocation. The constructor wraps
 * executor-owned arrays without copying; callers must not mutate those arrays during assembly.</p>
 */
public final class ResultRow {

    private final Object[] values;
    private final int[] columnIndexes;

    /**
     * Creates a transient view over converted values and their compile-time mapping indexes.
     * Both arrays must remain unchanged for the lifetime of this view.
     */
    public ResultRow(Object[] values, int[] columnIndexes) {
        this.values = Objects.requireNonNull(values, "values");
        this.columnIndexes = Objects.requireNonNull(columnIndexes, "columnIndexes");
    }

    /** Returns the converted value for one compile-time result-mapping entry. */
    public Object get(int mappingIndex) {
        if (mappingIndex < 0 || mappingIndex >= columnIndexes.length) {
            throw new IndexOutOfBoundsException("Result mapping index: " + mappingIndex);
        }
        return values[columnIndexes[mappingIndex]];
    }
}

package org.liteorm.api;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Reads and writes one Java value through one JDBC representation.
 *
 * <p>A handler owns one value conversion after the runtime type router selects it.
 * Implementations must be thread-safe.</p>
 *
 * @param <T> Java value type
 */
public interface TypeHandler<T> {

    /** Writes one nullable statement parameter. */
    default void setParameter(
            PreparedStatement statement, int index, T value, JDBCType jdbcType) throws SQLException {
        if (value == null) {
            setNull(statement, index, jdbcType);
            return;
        }
        setNonNull(statement, index, value, jdbcType);
    }

    /** Writes one non-null statement parameter. */
    void setNonNull(
        PreparedStatement statement,
        int index,
        T value,
        JDBCType jdbcType
    ) throws SQLException;

    /** Writes one null statement parameter. */
    default void setNull(PreparedStatement statement, int index, JDBCType jdbcType) throws SQLException {
        statement.setNull(index, jdbcType.getVendorTypeNumber());
    }

    /** Reads one nullable value from the current result row. */
    T getResult(ResultSet resultSet, int columnIndex) throws SQLException;
}

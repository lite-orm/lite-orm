package org.liteorm.api;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one Java value to and from one JDBC column.
 *
 * <p>Adapter instances handle writes, read nullable column values inside the executor-owned
 * JDBC lifecycle, and are reused by a generated Mapper. LiteORM supplies standard null binding,
 * which an adapter may override when its driver rejects the declared JDBC type for null values.
 * Implementations must therefore be thread-safe.</p>
 *
 * @param <T> Java value type
 */
public interface JdbcValueAdapter<T> {

    /**
     * Binds one non-null value.
     */
    void setNonNull(PreparedStatement statement, int index, T value, JDBCType jdbcType) throws SQLException;

    /**
     * Binds one null value using the declared JDBC type.
     *
     * <p>Adapters should override this only for a documented driver compatibility requirement.</p>
     */
    default void setNull(PreparedStatement statement, int index, JDBCType jdbcType) throws SQLException {
        statement.setNull(index, jdbcType.getVendorTypeNumber());
    }

    /**
     * Reads one nullable column value from the current result row.
     */
    T getNullable(ResultSet resultSet, int columnIndex) throws SQLException;
}

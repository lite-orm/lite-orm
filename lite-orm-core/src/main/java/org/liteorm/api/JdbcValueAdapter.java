package org.liteorm.api;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one Java value to and from one JDBC column.
 *
 * <p>LiteORM owns null parameter binding. Adapter instances handle non-null writes,
 * read nullable column values inside the executor-owned JDBC lifecycle, and are reused
 * by a generated Mapper. Implementations must therefore be thread-safe.</p>
 *
 * @param <T> Java value type
 */
public interface JdbcValueAdapter<T> {

    /**
     * Binds one non-null value.
     */
    void setNonNull(PreparedStatement statement, int index, T value, JDBCType jdbcType) throws SQLException;

    /**
     * Reads one nullable column value from the current result row.
     */
    T getNullable(ResultSet resultSet, int columnIndex) throws SQLException;
}

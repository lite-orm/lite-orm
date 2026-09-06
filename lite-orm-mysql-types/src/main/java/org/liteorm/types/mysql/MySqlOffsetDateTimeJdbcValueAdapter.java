package org.liteorm.types.mysql;

import org.liteorm.api.JdbcValueAdapter;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * Preserves instants stored in MySQL TIMESTAMP columns through the JDBC 4.2 contract.
 */
public final class MySqlOffsetDateTimeJdbcValueAdapter
        implements JdbcValueAdapter<OffsetDateTime> {

    /**
     * Creates a stateless OffsetDateTime adapter.
     */
    public MySqlOffsetDateTimeJdbcValueAdapter() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, OffsetDateTime value, JDBCType jdbcType)
            throws SQLException {
        statement.setObject(index, value, jdbcType.getVendorTypeNumber());
    }

    @Override
    public OffsetDateTime getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, OffsetDateTime.class);
    }
}

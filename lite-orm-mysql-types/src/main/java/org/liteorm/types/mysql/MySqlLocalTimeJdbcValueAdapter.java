package org.liteorm.types.mysql;

import org.liteorm.api.JdbcValueAdapter;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;

/**
 * Preserves MySQL TIME values through the JDBC 4.2 LocalTime contract.
 */
public final class MySqlLocalTimeJdbcValueAdapter implements JdbcValueAdapter<LocalTime> {

    /**
     * Creates a stateless LocalTime adapter.
     */
    public MySqlLocalTimeJdbcValueAdapter() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, LocalTime value, JDBCType jdbcType) throws SQLException {
        statement.setObject(index, value, jdbcType.getVendorTypeNumber());
    }

    @Override
    public LocalTime getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, LocalTime.class);
    }
}

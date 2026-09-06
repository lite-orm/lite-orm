package org.liteorm.types.mysql;

import org.liteorm.api.JdbcValueAdapter;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Maps UUID values to MySQL CHAR(36) columns.
 */
public final class MySqlUuidJdbcValueAdapter implements JdbcValueAdapter<UUID> {

    /**
     * Creates a stateless UUID adapter.
     */
    public MySqlUuidJdbcValueAdapter() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, UUID value, JDBCType jdbcType) throws SQLException {
        statement.setString(index, value.toString());
    }

    @Override
    public UUID getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
        String value = resultSet.getString(columnIndex);
        return value == null ? null : UUID.fromString(value);
    }
}

package org.liteorm.types.mysql;

import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Maps UUID values to MySQL CHAR(36) columns.
 */
public final class MySqlUuidTypeHandler implements TypeHandler<UUID> {

    /**
     * Creates a stateless UUID type handler.
     */
    public MySqlUuidTypeHandler() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, UUID value, JDBCType jdbcType) throws SQLException {
        statement.setString(index, value.toString());
    }

    @Override
    public UUID getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        String value = resultSet.getString(columnIndex);
        return value == null ? null : UUID.fromString(value);
    }
}

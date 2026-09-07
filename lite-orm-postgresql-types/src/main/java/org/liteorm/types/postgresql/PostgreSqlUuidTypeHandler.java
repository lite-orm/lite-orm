package org.liteorm.types.postgresql;

import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Preserves PostgreSQL native UUID values through the JDBC 4.2 object contract.
 */
public final class PostgreSqlUuidTypeHandler implements TypeHandler<UUID> {

    /**
     * Creates a stateless UUID type handler.
     */
    public PostgreSqlUuidTypeHandler() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, UUID value, JDBCType jdbcType) throws SQLException {
        statement.setObject(index, value, jdbcType.getVendorTypeNumber());
    }

    @Override
    public UUID getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, UUID.class);
    }
}

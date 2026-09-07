package org.liteorm.types.postgresql;

import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * Preserves PostgreSQL TIMESTAMP WITH TIME ZONE instants through the JDBC 4.2 contract.
 */
public final class PostgreSqlOffsetDateTimeTypeHandler
        implements TypeHandler<OffsetDateTime> {

    /**
     * Creates a stateless OffsetDateTime type handler.
     */
    public PostgreSqlOffsetDateTimeTypeHandler() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, OffsetDateTime value, JDBCType jdbcType)
            throws SQLException {
        statement.setObject(index, value, jdbcType.getVendorTypeNumber());
    }

    @Override
    public OffsetDateTime getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, OffsetDateTime.class);
    }
}

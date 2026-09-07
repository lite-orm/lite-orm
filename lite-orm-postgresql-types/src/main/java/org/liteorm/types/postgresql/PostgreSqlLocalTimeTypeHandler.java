package org.liteorm.types.postgresql;

import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;

/**
 * Preserves PostgreSQL TIME values through the JDBC 4.2 LocalTime contract.
 */
public final class PostgreSqlLocalTimeTypeHandler implements TypeHandler<LocalTime> {

    /**
     * Creates a stateless LocalTime type handler.
     */
    public PostgreSqlLocalTimeTypeHandler() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, LocalTime value, JDBCType jdbcType) throws SQLException {
        statement.setObject(index, value, jdbcType.getVendorTypeNumber());
    }

    @Override
    public LocalTime getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, LocalTime.class);
    }
}

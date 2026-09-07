package org.liteorm.types.postgresql;

import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetTime;

/** Preserves PostgreSQL time-with-time-zone values through the JDBC 4.2 object contract. */
public final class PostgreSqlOffsetTimeTypeHandler implements TypeHandler<OffsetTime> {

    /** Creates a stateless type handler. */
    public PostgreSqlOffsetTimeTypeHandler() {
    }

    @Override
    public void setNonNull(
            PreparedStatement statement, int index, OffsetTime value, JDBCType jdbcType) throws SQLException {
        statement.setObject(index, value);
    }

    @Override
    public OffsetTime getResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getObject(columnIndex, OffsetTime.class);
    }
}

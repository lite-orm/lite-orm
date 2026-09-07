package org.liteorm.test;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.TypeHandler;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@JdbcTypeMapping(
    javaType = UUID.class,
    jdbcType = JDBCType.VARCHAR,
    handler = TestJdbcTypeMappings.UuidTypeHandler.class)
public final class TestJdbcTypeMappings implements JdbcTypeMappings {

    public static final class UuidTypeHandler implements TypeHandler<UUID> {

        public UuidTypeHandler() {
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
}

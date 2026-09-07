package org.liteorm.types.mysql;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.TypeHandler;
import org.liteorm.jdbc.StandardJdbcTypeMappings;

import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;
import java.util.UUID;

/**
 * Official MySQL JDBC value mappings supplied by LiteORM.
 */
@JdbcTypeMapping(
    javaType = BigInteger.class,
    jdbcType = JDBCType.DECIMAL,
    handler = StandardJdbcTypeMappings.BigIntegerTypeHandler.class
)
@JdbcTypeMapping(
    javaType = Byte[].class,
    jdbcType = JDBCType.VARBINARY,
    handler = StandardJdbcTypeMappings.BoxedByteArrayTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.TIMESTAMP,
    handler = StandardJdbcTypeMappings.UtilDateTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.DATE,
    handler = StandardJdbcTypeMappings.UtilDateOnlyTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.TIME,
    handler = StandardJdbcTypeMappings.UtilTimeOnlyTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.sql.Date.class,
    jdbcType = JDBCType.DATE,
    handler = StandardJdbcTypeMappings.SqlDateTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.sql.Time.class,
    jdbcType = JDBCType.TIME,
    handler = StandardJdbcTypeMappings.SqlTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = java.sql.Timestamp.class,
    jdbcType = JDBCType.TIMESTAMP,
    handler = StandardJdbcTypeMappings.SqlTimestampTypeHandler.class
)
@JdbcTypeMapping(
    javaType = Year.class,
    jdbcType = JDBCType.INTEGER,
    handler = StandardJdbcTypeMappings.YearTypeHandler.class
)
@JdbcTypeMapping(
    javaType = Month.class,
    jdbcType = JDBCType.INTEGER,
    handler = StandardJdbcTypeMappings.MonthTypeHandler.class
)
@JdbcTypeMapping(
    javaType = YearMonth.class,
    jdbcType = JDBCType.VARCHAR,
    handler = StandardJdbcTypeMappings.YearMonthTypeHandler.class
)
@JdbcTypeMapping(
    javaType = JapaneseDate.class,
    jdbcType = JDBCType.DATE,
    handler = StandardJdbcTypeMappings.JapaneseDateTypeHandler.class
)
@JdbcTypeMapping(
    javaType = UUID.class,
    jdbcType = JDBCType.CHAR,
    handler = MySqlUuidTypeHandler.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    handler = MySqlLocalTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP,
    handler = MySqlOffsetDateTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NCHAR,
    handler = MySqlJdbcTypeMappings.NationalStringTypeHandler.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NVARCHAR,
    handler = MySqlJdbcTypeMappings.NationalStringTypeHandler.class
)
public final class MySqlJdbcTypeMappings implements JdbcTypeMappings {

    /** Maps national-character values through the JDBC national string methods. */
    public static final class NationalStringTypeHandler implements TypeHandler<String> {

        /** Creates a stateless type handler. */
        public NationalStringTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, String value, JDBCType jdbcType) throws SQLException {
            statement.setNString(index, value);
        }

        @Override
        public String getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getNString(columnIndex);
        }
    }
}

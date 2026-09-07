package org.liteorm.types.postgresql;

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
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;
import java.util.UUID;

/**
 * Official PostgreSQL JDBC value mappings supplied by LiteORM.
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
    jdbcType = JDBCType.OTHER,
    vendorTypeName = "uuid",
    handler = PostgreSqlUuidTypeHandler.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    handler = PostgreSqlLocalTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE,
    handler = PostgreSqlOffsetDateTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = OffsetTime.class,
    jdbcType = JDBCType.TIME_WITH_TIMEZONE,
    handler = PostgreSqlOffsetTimeTypeHandler.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NCHAR,
    handler = PostgreSqlJdbcTypeMappings.NationalStringTypeHandler.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NVARCHAR,
    handler = PostgreSqlJdbcTypeMappings.NationalStringTypeHandler.class
)
public final class PostgreSqlJdbcTypeMappings implements JdbcTypeMappings {

    /**
     * Preserves national-character values through PostgreSQL's ordinary Unicode string methods.
     * pgjdbc does not implement JDBC {@code setNString} or {@code getNString}.
     */
    public static final class NationalStringTypeHandler implements TypeHandler<String> {

        /** Creates a stateless type handler. */
        public NationalStringTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, String value, JDBCType jdbcType) throws SQLException {
            statement.setString(index, value);
        }

        @Override
        public void setNull(PreparedStatement statement, int index, JDBCType jdbcType) throws SQLException {
            statement.setNull(index, JDBCType.VARCHAR.getVendorTypeNumber());
        }

        @Override
        public String getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getString(columnIndex);
        }
    }
}

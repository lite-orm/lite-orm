package org.liteorm.types.postgresql;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.JdbcValueAdapter;
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
    adapter = StandardJdbcTypeMappings.BigIntegerJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = Byte[].class,
    jdbcType = JDBCType.VARBINARY,
    adapter = StandardJdbcTypeMappings.BoxedByteArrayJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.TIMESTAMP,
    adapter = StandardJdbcTypeMappings.UtilDateJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.DATE,
    adapter = StandardJdbcTypeMappings.UtilDateOnlyJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.util.Date.class,
    jdbcType = JDBCType.TIME,
    adapter = StandardJdbcTypeMappings.UtilTimeOnlyJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.sql.Date.class,
    jdbcType = JDBCType.DATE,
    adapter = StandardJdbcTypeMappings.SqlDateJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.sql.Time.class,
    jdbcType = JDBCType.TIME,
    adapter = StandardJdbcTypeMappings.SqlTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = java.sql.Timestamp.class,
    jdbcType = JDBCType.TIMESTAMP,
    adapter = StandardJdbcTypeMappings.SqlTimestampJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = Year.class,
    jdbcType = JDBCType.INTEGER,
    adapter = StandardJdbcTypeMappings.YearJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = Month.class,
    jdbcType = JDBCType.INTEGER,
    adapter = StandardJdbcTypeMappings.MonthJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = YearMonth.class,
    jdbcType = JDBCType.VARCHAR,
    adapter = StandardJdbcTypeMappings.YearMonthJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = JapaneseDate.class,
    jdbcType = JDBCType.DATE,
    adapter = StandardJdbcTypeMappings.JapaneseDateJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = UUID.class,
    jdbcType = JDBCType.OTHER,
    adapter = PostgreSqlUuidJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    adapter = PostgreSqlLocalTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE,
    adapter = PostgreSqlOffsetDateTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = OffsetTime.class,
    jdbcType = JDBCType.TIME_WITH_TIMEZONE,
    adapter = PostgreSqlOffsetTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NCHAR,
    adapter = PostgreSqlJdbcTypeMappings.NationalStringJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NVARCHAR,
    adapter = PostgreSqlJdbcTypeMappings.NationalStringJdbcValueAdapter.class
)
public final class PostgreSqlJdbcTypeMappings implements JdbcTypeMappings {

    /**
     * Preserves national-character values through PostgreSQL's ordinary Unicode string methods.
     * pgjdbc does not implement JDBC {@code setNString} or {@code getNString}.
     */
    public static final class NationalStringJdbcValueAdapter implements JdbcValueAdapter<String> {

        /** Creates a stateless adapter. */
        public NationalStringJdbcValueAdapter() {
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
        public String getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getString(columnIndex);
        }
    }
}

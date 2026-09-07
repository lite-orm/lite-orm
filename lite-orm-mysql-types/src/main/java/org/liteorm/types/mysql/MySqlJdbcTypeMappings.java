package org.liteorm.types.mysql;

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
    jdbcType = JDBCType.CHAR,
    adapter = MySqlUuidJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    adapter = MySqlLocalTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP,
    adapter = MySqlOffsetDateTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NCHAR,
    adapter = MySqlJdbcTypeMappings.NationalStringJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = String.class,
    jdbcType = JDBCType.NVARCHAR,
    adapter = MySqlJdbcTypeMappings.NationalStringJdbcValueAdapter.class
)
public final class MySqlJdbcTypeMappings implements JdbcTypeMappings {

    /** Maps national-character values through the JDBC national string methods. */
    public static final class NationalStringJdbcValueAdapter implements JdbcValueAdapter<String> {

        /** Creates a stateless adapter. */
        public NationalStringJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, String value, JDBCType jdbcType) throws SQLException {
            statement.setNString(index, value);
        }

        @Override
        public String getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getNString(columnIndex);
        }
    }
}

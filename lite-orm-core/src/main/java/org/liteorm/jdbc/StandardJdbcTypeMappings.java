package org.liteorm.jdbc;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.JdbcValueAdapter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;

/**
 * Selectable database-independent JDBC value mappings and reusable standard adapter implementations.
 *
 * <p>Official database-family collections explicitly declare their complete mapping sets and may
 * reference these adapters. The compiler does not append this collection implicitly.</p>
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
public final class StandardJdbcTypeMappings implements JdbcTypeMappings {

    private StandardJdbcTypeMappings() {
    }

    /** Maps enum constants through their declared names. */
    public static final class EnumNameJdbcValueAdapter<E extends Enum<E>> implements JdbcValueAdapter<E> {

        private final Class<E> enumType;

        /** Creates an adapter for one concrete enum type. */
        public EnumNameJdbcValueAdapter(Class<E> enumType) {
            this.enumType = java.util.Objects.requireNonNull(enumType, "enumType");
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, E value, JDBCType jdbcType) throws SQLException {
            statement.setString(index, value.name());
        }

        @Override
        public E getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            String value = resultSet.getString(columnIndex);
            return value == null ? null : Enum.valueOf(enumType, value);
        }
    }

    /** Maps enum constants through their zero-based declaration ordinal. */
    public static final class EnumOrdinalJdbcValueAdapter<E extends Enum<E>> implements JdbcValueAdapter<E> {

        private final E[] constants;

        /** Creates an adapter for one concrete enum type. */
        public EnumOrdinalJdbcValueAdapter(Class<E> enumType) {
            this.constants = java.util.Objects.requireNonNull(enumType, "enumType").getEnumConstants();
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, E value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.ordinal());
        }

        @Override
        public E getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            int ordinal = resultSet.getInt(columnIndex);
            if (resultSet.wasNull()) {
                return null;
            }
            if (ordinal < 0 || ordinal >= constants.length) {
                throw new SQLException("Enum ordinal " + ordinal + " is outside the declared constant range");
            }
            return constants[ordinal];
        }
    }

    /** Maps arbitrary-precision integer values through JDBC {@link BigDecimal} values. */
    public static final class BigIntegerJdbcValueAdapter implements JdbcValueAdapter<BigInteger> {

        /** Creates a stateless adapter. */
        public BigIntegerJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, BigInteger value, JDBCType jdbcType) throws SQLException {
            statement.setBigDecimal(index, new BigDecimal(value));
        }

        @Override
        public BigInteger getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            BigDecimal value = resultSet.getBigDecimal(columnIndex);
            return value == null ? null : value.toBigInteger();
        }
    }

    /** Maps boxed byte arrays through JDBC binary values. */
    public static final class BoxedByteArrayJdbcValueAdapter implements JdbcValueAdapter<Byte[]> {

        /** Creates a stateless adapter. */
        public BoxedByteArrayJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, Byte[] value, JDBCType jdbcType) throws SQLException {
            byte[] bytes = new byte[value.length];
            for (int byteIndex = 0; byteIndex < value.length; byteIndex++) {
                bytes[byteIndex] = value[byteIndex];
            }
            statement.setBytes(index, bytes);
        }

        @Override
        public Byte[] getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            byte[] bytes = resultSet.getBytes(columnIndex);
            if (bytes == null) {
                return null;
            }
            Byte[] boxed = new Byte[bytes.length];
            for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
                boxed[byteIndex] = bytes[byteIndex];
            }
            return boxed;
        }
    }

    /** Maps legacy {@link java.util.Date} values as timestamps. */
    public static final class UtilDateJdbcValueAdapter implements JdbcValueAdapter<java.util.Date> {

        /** Creates a stateless adapter. */
        public UtilDateJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setTimestamp(index, new java.sql.Timestamp(value.getTime()));
        }

        @Override
        public java.util.Date getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Timestamp value = resultSet.getTimestamp(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps legacy {@link java.util.Date} values using only their JDBC date representation. */
    public static final class UtilDateOnlyJdbcValueAdapter implements JdbcValueAdapter<java.util.Date> {

        /** Creates a stateless adapter. */
        public UtilDateOnlyJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, new java.sql.Date(value.getTime()));
        }

        @Override
        public java.util.Date getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Date value = resultSet.getDate(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps legacy {@link java.util.Date} values using only their JDBC time representation. */
    public static final class UtilTimeOnlyJdbcValueAdapter implements JdbcValueAdapter<java.util.Date> {

        /** Creates a stateless adapter. */
        public UtilTimeOnlyJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setTime(index, new java.sql.Time(value.getTime()));
        }

        @Override
        public java.util.Date getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Time value = resultSet.getTime(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps JDBC date values without a time component. */
    public static final class SqlDateJdbcValueAdapter implements JdbcValueAdapter<java.sql.Date> {

        /** Creates a stateless adapter. */
        public SqlDateJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, value);
        }

        @Override
        public java.sql.Date getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getDate(columnIndex);
        }
    }

    /** Maps JDBC time values without a date component. */
    public static final class SqlTimeJdbcValueAdapter implements JdbcValueAdapter<java.sql.Time> {

        /** Creates a stateless adapter. */
        public SqlTimeJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Time value, JDBCType jdbcType)
                throws SQLException {
            statement.setTime(index, value);
        }

        @Override
        public java.sql.Time getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getTime(columnIndex);
        }
    }

    /** Maps JDBC timestamp values. */
    public static final class SqlTimestampJdbcValueAdapter implements JdbcValueAdapter<java.sql.Timestamp> {

        /** Creates a stateless adapter. */
        public SqlTimestampJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Timestamp value, JDBCType jdbcType)
                throws SQLException {
            statement.setTimestamp(index, value);
        }

        @Override
        public java.sql.Timestamp getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getTimestamp(columnIndex);
        }
    }

    /** Maps years to their ISO numeric value. */
    public static final class YearJdbcValueAdapter implements JdbcValueAdapter<Year> {

        /** Creates a stateless adapter. */
        public YearJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, Year value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.getValue());
        }

        @Override
        public Year getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            int value = resultSet.getInt(columnIndex);
            return resultSet.wasNull() ? null : Year.of(value);
        }
    }

    /** Maps months to their ISO number from 1 through 12. */
    public static final class MonthJdbcValueAdapter implements JdbcValueAdapter<Month> {

        /** Creates a stateless adapter. */
        public MonthJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, Month value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.getValue());
        }

        @Override
        public Month getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            int value = resultSet.getInt(columnIndex);
            return resultSet.wasNull() ? null : Month.of(value);
        }
    }

    /** Maps year-month values through their ISO-8601 text representation. */
    public static final class YearMonthJdbcValueAdapter implements JdbcValueAdapter<YearMonth> {

        /** Creates a stateless adapter. */
        public YearMonthJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, YearMonth value, JDBCType jdbcType) throws SQLException {
            statement.setString(index, value.toString());
        }

        @Override
        public YearMonth getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            String value = resultSet.getString(columnIndex);
            return value == null ? null : YearMonth.parse(value);
        }
    }

    /** Maps Japanese calendar dates through an equivalent ISO SQL date. */
    public static final class JapaneseDateJdbcValueAdapter implements JdbcValueAdapter<JapaneseDate> {

        /** Creates a stateless adapter. */
        public JapaneseDateJdbcValueAdapter() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, JapaneseDate value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, java.sql.Date.valueOf(LocalDate.from(value)));
        }

        @Override
        public JapaneseDate getNullable(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Date value = resultSet.getDate(columnIndex);
            return value == null ? null : JapaneseDate.from(value.toLocalDate());
        }
    }
}

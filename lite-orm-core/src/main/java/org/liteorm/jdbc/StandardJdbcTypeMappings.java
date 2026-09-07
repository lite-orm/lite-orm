package org.liteorm.jdbc;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;
import org.liteorm.api.TypeHandler;

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
import java.util.function.Function;

/**
 * Selectable database-independent JDBC value mappings and reusable standard type-handler implementations.
 *
 * <p>Official database-family collections explicitly declare their complete mapping sets and may
 * reference these handlers. The compiler does not append this collection implicitly.</p>
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
public final class StandardJdbcTypeMappings implements JdbcTypeMappings {

    private StandardJdbcTypeMappings() {
    }

    /** Maps enum constants through their declared names. */
    public static final class EnumNameTypeHandler<E extends Enum<E>> implements TypeHandler<E> {

        private final Function<String, E> valueOf;

        /** Creates a type handler for one concrete enum type. */
        public EnumNameTypeHandler(Function<String, E> valueOf) {
            this.valueOf = java.util.Objects.requireNonNull(valueOf, "valueOf");
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, E value, JDBCType jdbcType) throws SQLException {
            statement.setString(index, value.name());
        }

        @Override
        public E getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            String value = resultSet.getString(columnIndex);
            return value == null ? null : valueOf.apply(value);
        }
    }

    /** Maps enum constants through their zero-based declaration ordinal. */
    public static final class EnumOrdinalTypeHandler<E extends Enum<E>> implements TypeHandler<E> {

        private final E[] constants;

        /** Creates a type handler for one concrete enum type. */
        public EnumOrdinalTypeHandler(E[] constants) {
            this.constants = java.util.Objects.requireNonNull(constants, "constants").clone();
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, E value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.ordinal());
        }

        @Override
        public E getResult(ResultSet resultSet, int columnIndex) throws SQLException {
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
    public static final class BigIntegerTypeHandler implements TypeHandler<BigInteger> {

        /** Creates a stateless type handler. */
        public BigIntegerTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, BigInteger value, JDBCType jdbcType) throws SQLException {
            statement.setBigDecimal(index, new BigDecimal(value));
        }

        @Override
        public BigInteger getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            BigDecimal value = resultSet.getBigDecimal(columnIndex);
            return value == null ? null : value.toBigInteger();
        }
    }

    /** Maps boxed byte arrays through JDBC binary values. */
    public static final class BoxedByteArrayTypeHandler implements TypeHandler<Byte[]> {

        /** Creates a stateless type handler. */
        public BoxedByteArrayTypeHandler() {
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
        public Byte[] getResult(ResultSet resultSet, int columnIndex) throws SQLException {
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
    public static final class UtilDateTypeHandler implements TypeHandler<java.util.Date> {

        /** Creates a stateless type handler. */
        public UtilDateTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setTimestamp(index, new java.sql.Timestamp(value.getTime()));
        }

        @Override
        public java.util.Date getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Timestamp value = resultSet.getTimestamp(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps legacy {@link java.util.Date} values using only their JDBC date representation. */
    public static final class UtilDateOnlyTypeHandler implements TypeHandler<java.util.Date> {

        /** Creates a stateless type handler. */
        public UtilDateOnlyTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, new java.sql.Date(value.getTime()));
        }

        @Override
        public java.util.Date getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Date value = resultSet.getDate(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps legacy {@link java.util.Date} values using only their JDBC time representation. */
    public static final class UtilTimeOnlyTypeHandler implements TypeHandler<java.util.Date> {

        /** Creates a stateless type handler. */
        public UtilTimeOnlyTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.util.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setTime(index, new java.sql.Time(value.getTime()));
        }

        @Override
        public java.util.Date getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Time value = resultSet.getTime(columnIndex);
            return value == null ? null : new java.util.Date(value.getTime());
        }
    }

    /** Maps JDBC date values without a time component. */
    public static final class SqlDateTypeHandler implements TypeHandler<java.sql.Date> {

        /** Creates a stateless type handler. */
        public SqlDateTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Date value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, value);
        }

        @Override
        public java.sql.Date getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getDate(columnIndex);
        }
    }

    /** Maps JDBC time values without a date component. */
    public static final class SqlTimeTypeHandler implements TypeHandler<java.sql.Time> {

        /** Creates a stateless type handler. */
        public SqlTimeTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Time value, JDBCType jdbcType)
                throws SQLException {
            statement.setTime(index, value);
        }

        @Override
        public java.sql.Time getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getTime(columnIndex);
        }
    }

    /** Maps JDBC timestamp values. */
    public static final class SqlTimestampTypeHandler implements TypeHandler<java.sql.Timestamp> {

        /** Creates a stateless type handler. */
        public SqlTimestampTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, java.sql.Timestamp value, JDBCType jdbcType)
                throws SQLException {
            statement.setTimestamp(index, value);
        }

        @Override
        public java.sql.Timestamp getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            return resultSet.getTimestamp(columnIndex);
        }
    }

    /** Maps years to their ISO numeric value. */
    public static final class YearTypeHandler implements TypeHandler<Year> {

        /** Creates a stateless type handler. */
        public YearTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, Year value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.getValue());
        }

        @Override
        public Year getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            int value = resultSet.getInt(columnIndex);
            return resultSet.wasNull() ? null : Year.of(value);
        }
    }

    /** Maps months to their ISO number from 1 through 12. */
    public static final class MonthTypeHandler implements TypeHandler<Month> {

        /** Creates a stateless type handler. */
        public MonthTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, Month value, JDBCType jdbcType) throws SQLException {
            statement.setInt(index, value.getValue());
        }

        @Override
        public Month getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            int value = resultSet.getInt(columnIndex);
            return resultSet.wasNull() ? null : Month.of(value);
        }
    }

    /** Maps year-month values through their ISO-8601 text representation. */
    public static final class YearMonthTypeHandler implements TypeHandler<YearMonth> {

        /** Creates a stateless type handler. */
        public YearMonthTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, YearMonth value, JDBCType jdbcType) throws SQLException {
            statement.setString(index, value.toString());
        }

        @Override
        public YearMonth getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            String value = resultSet.getString(columnIndex);
            return value == null ? null : YearMonth.parse(value);
        }
    }

    /** Maps Japanese calendar dates through an equivalent ISO SQL date. */
    public static final class JapaneseDateTypeHandler implements TypeHandler<JapaneseDate> {

        /** Creates a stateless type handler. */
        public JapaneseDateTypeHandler() {
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, JapaneseDate value, JDBCType jdbcType)
                throws SQLException {
            statement.setDate(index, java.sql.Date.valueOf(LocalDate.from(value)));
        }

        @Override
        public JapaneseDate getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            java.sql.Date value = resultSet.getDate(columnIndex);
            return value == null ? null : JapaneseDate.from(value.toLocalDate());
        }
    }
}

package org.liteorm.types.postgresql.fixture;

import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Batch;
import org.liteorm.annotation.GeneratedKey;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.ResultJdbcType;
import org.liteorm.annotation.Select;

import java.math.BigInteger;
import java.sql.JDBCType;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PostgreSqlStandardTypesMapper {

    record IntegerRow(long id, BigInteger value) {
    }

    record StandardValueRecord(
        BigInteger integerValue,
        @ResultJdbcType(JDBCType.INTEGER) StandardStatus enumOrdinalValue
    ) {
    }

    class StandardValueBean {
        private BigInteger integerValue;

        @ResultJdbcType(JDBCType.INTEGER)
        private StandardStatus enumOrdinalValue;

        public StandardValueBean() {
        }

        public BigInteger getIntegerValue() {
            return integerValue;
        }

        public void setIntegerValue(BigInteger integerValue) {
            this.integerValue = integerValue;
        }

        public StandardStatus getEnumOrdinalValue() {
            return enumOrdinalValue;
        }

        public void setEnumOrdinalValue(StandardStatus enumOrdinalValue) {
            this.enumOrdinalValue = enumOrdinalValue;
        }
    }

    @Insert("INSERT INTO postgresql_standard_type_values (id, integer_value, binary_value) "
        + "VALUES (#{id}, #{integerValue}, #{binaryValue})")
    int insert(
        @Param("id") long id,
        @Param("integerValue") BigInteger integerValue,
        @Param("binaryValue") Byte[] binaryValue
    );

    @Select("SELECT integer_value FROM postgresql_standard_type_values WHERE id = #{id}")
    BigInteger findInteger(@Param("id") long id);

    @Select("SELECT integer_value FROM postgresql_standard_type_values "
        + "WHERE integer_value IS NOT NULL ORDER BY id")
    List<BigInteger> findIntegers();

    @Select("SELECT integer_value FROM postgresql_standard_type_values WHERE id = #{id}")
    Optional<BigInteger> findOptionalInteger(@Param("id") long id);

    @Insert("<script>INSERT INTO postgresql_standard_type_values (id, integer_value) "
        + "<if test=\"value != null\">VALUES (#{id}, #{value})</if></script>")
    int insertIntegerDynamic(@Param("id") long id, @Param("value") BigInteger value);

    @Batch("INSERT INTO postgresql_standard_type_values (id, integer_value) "
        + "VALUES (#{item.id}, #{item.value})")
    int[] insertIntegerBatch(List<IntegerRow> rows);

    @Insert("INSERT INTO postgresql_standard_type_values (id, integer_value, enum_ordinal_value) "
        + "VALUES (#{id}, #{integerValue}, #{enumValue,jdbcType=INTEGER})")
    int insertStandardComposite(
        @Param("id") long id,
        @Param("integerValue") BigInteger integerValue,
        @Param("enumValue") StandardStatus enumValue
    );

    @Select("SELECT integer_value AS integerValue, enum_ordinal_value AS enumOrdinalValue "
        + "FROM postgresql_standard_type_values WHERE id = #{id}")
    StandardValueRecord findStandardRecord(@Param("id") long id);

    @Select("SELECT integer_value AS integerValue, enum_ordinal_value AS enumOrdinalValue "
        + "FROM postgresql_standard_type_values WHERE id = #{id}")
    StandardValueBean findStandardBean(@Param("id") long id);

    @GeneratedKey("id")
    @Insert("INSERT INTO postgresql_standard_generated_keys (description) VALUES (#{description})")
    BigInteger insertGeneratedKey(@Param("description") String description);

    @Select("SELECT binary_value FROM postgresql_standard_type_values WHERE id = #{id}")
    Byte[] findBinary(@Param("id") long id);

    @Insert("INSERT INTO postgresql_standard_type_values "
        + "(id, util_date_value, sql_date_value, sql_time_value, sql_timestamp_value) "
        + "VALUES (#{id}, #{utilDate}, #{sqlDate}, #{sqlTime}, #{sqlTimestamp})")
    int insertLegacyDates(
        @Param("id") long id,
        @Param("utilDate") java.util.Date utilDate,
        @Param("sqlDate") java.sql.Date sqlDate,
        @Param("sqlTime") java.sql.Time sqlTime,
        @Param("sqlTimestamp") java.sql.Timestamp sqlTimestamp
    );

    @Select("SELECT util_date_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.util.Date findUtilDate(@Param("id") long id);

    @Select("SELECT sql_date_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.sql.Date findSqlDate(@Param("id") long id);

    @Select("SELECT sql_time_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.sql.Time findSqlTime(@Param("id") long id);

    @Select("SELECT sql_timestamp_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.sql.Timestamp findSqlTimestamp(@Param("id") long id);

    @Insert("INSERT INTO postgresql_standard_type_values "
        + "(id, util_date_only_value, util_time_only_value) "
        + "VALUES (#{id}, #{dateValue,jdbcType=DATE}, #{timeValue,jdbcType=TIME})")
    int insertLegacyDateOnlyValues(
        @Param("id") long id,
        @Param("dateValue") java.util.Date dateValue,
        @Param("timeValue") java.util.Date timeValue
    );

    @ResultJdbcType(JDBCType.DATE)
    @Select("SELECT util_date_only_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.util.Date findUtilDateOnly(@Param("id") long id);

    @ResultJdbcType(JDBCType.TIME)
    @Select("SELECT util_time_only_value FROM postgresql_standard_type_values WHERE id = #{id}")
    java.util.Date findUtilTimeOnly(@Param("id") long id);

    @Insert("INSERT INTO postgresql_standard_type_values "
        + "(id, year_value, month_value, year_month_value, japanese_date_value) "
        + "VALUES (#{id}, #{year}, #{month}, #{yearMonth}, #{japaneseDate})")
    int insertCalendarValues(
        @Param("id") long id,
        @Param("year") Year year,
        @Param("month") Month month,
        @Param("yearMonth") YearMonth yearMonth,
        @Param("japaneseDate") JapaneseDate japaneseDate
    );

    @Select("SELECT year_value FROM postgresql_standard_type_values WHERE id = #{id}")
    Year findYear(@Param("id") long id);

    @Select("SELECT month_value FROM postgresql_standard_type_values WHERE id = #{id}")
    Month findMonth(@Param("id") long id);

    @Select("SELECT year_month_value FROM postgresql_standard_type_values WHERE id = #{id}")
    YearMonth findYearMonth(@Param("id") long id);

    @Select("SELECT japanese_date_value FROM postgresql_standard_type_values WHERE id = #{id}")
    JapaneseDate findJapaneseDate(@Param("id") long id);

    @Insert("INSERT INTO postgresql_standard_type_values (id, enum_name_value, enum_ordinal_value) "
        + "VALUES (#{id}, #{nameValue}, #{ordinalValue,jdbcType=INTEGER})")
    int insertEnums(
        @Param("id") long id,
        @Param("nameValue") StandardStatus nameValue,
        @Param("ordinalValue") StandardStatus ordinalValue
    );

    @Select("SELECT enum_name_value FROM postgresql_standard_type_values WHERE id = #{id}")
    StandardStatus findEnumName(@Param("id") long id);

    @ResultJdbcType(JDBCType.INTEGER)
    @Select("SELECT enum_ordinal_value FROM postgresql_standard_type_values WHERE id = #{id}")
    StandardStatus findEnumOrdinal(@Param("id") long id);

    @Insert("INSERT INTO postgresql_standard_type_values "
        + "(id, national_char_value, national_varchar_value) "
        + "VALUES (#{id}, #{charValue,jdbcType=NCHAR}, #{varcharValue,jdbcType=NVARCHAR})")
    int insertNationalStrings(
        @Param("id") long id,
        @Param("charValue") String charValue,
        @Param("varcharValue") String varcharValue
    );

    @ResultJdbcType(JDBCType.NCHAR)
    @Select("SELECT national_char_value FROM postgresql_standard_type_values WHERE id = #{id}")
    String findNationalChar(@Param("id") long id);

    @ResultJdbcType(JDBCType.NVARCHAR)
    @Select("SELECT national_varchar_value FROM postgresql_standard_type_values WHERE id = #{id}")
    String findNationalVarchar(@Param("id") long id);
}

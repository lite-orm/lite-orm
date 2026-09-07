package org.liteorm.types.postgresql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.LiteOrm;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;
import org.liteorm.types.postgresql.fixture.PostgreSqlStandardTypesMapper;
import org.liteorm.types.postgresql.fixture.PostgreSqlStandardTypesMapperImpl;
import org.liteorm.types.postgresql.fixture.StandardStatus;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgreSqlStandardScalarMappingTest {

    private PostgreSqlStandardTypesMapper mapper;

    @BeforeEach
    void resetDatabase() throws Exception {
        TestDatabase database = TestDatabase.shared(DatabaseEngine.POSTGRESQL);
        DataSource dataSource = database.createDataSource();
        database.execute(dataSource, """
            CREATE TABLE postgresql_standard_type_values (
                id BIGINT PRIMARY KEY,
                integer_value NUMERIC(38, 0),
                binary_value BYTEA,
                util_date_value TIMESTAMP(6),
                util_date_only_value DATE,
                util_time_only_value TIME(6),
                sql_date_value DATE,
                sql_time_value TIME(6),
                sql_timestamp_value TIMESTAMP(6),
                year_value INTEGER,
                month_value INTEGER,
                year_month_value VARCHAR(7),
                japanese_date_value DATE,
                enum_name_value VARCHAR(32),
                enum_ordinal_value INTEGER,
                national_char_value VARCHAR(16),
                national_varchar_value VARCHAR(64)
            )
            """);
        database.execute(dataSource, """
            CREATE TABLE postgresql_standard_generated_keys (
                id BIGSERIAL PRIMARY KEY,
                description VARCHAR(64)
            )
            """);
        mapper = new PostgreSqlStandardTypesMapperImpl(
            LiteOrm.jdbc(dataSource).domain("postgresql-standard-scalar-types").build().sqlExecutor());
    }

    @Test
    void mapsBigIntegerAndBoxedBytesThroughStandardMappings() {
        BigInteger integerValue = new BigInteger("12345678901234567890123456789012345678");
        Byte[] binaryValue = {0, 1, 2, 127, -1};

        assertEquals(1, mapper.insert(1L, integerValue, binaryValue));
        assertEquals(1, mapper.insert(2L, null, null));

        assertEquals(integerValue, mapper.findInteger(1L));
        assertArrayEquals(binaryValue, mapper.findBinary(1L));
        assertNull(mapper.findInteger(2L));
        assertNull(mapper.findBinary(2L));
    }

    @Test
    void mapsLegacyDatesThroughStandardMappings() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 6, 7, 8, 9, 123_456_000);
        java.sql.Timestamp sqlTimestamp = java.sql.Timestamp.valueOf(dateTime);
        java.util.Date utilDate = new java.util.Date(sqlTimestamp.getTime());
        java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.of(2026, 9, 6));
        java.sql.Time sqlTime = java.sql.Time.valueOf(LocalTime.of(7, 8, 9));

        assertEquals(1, mapper.insertLegacyDates(10L, utilDate, sqlDate, sqlTime, sqlTimestamp));
        assertEquals(1, mapper.insertLegacyDates(11L, null, null, null, null));

        assertEquals(utilDate, mapper.findUtilDate(10L));
        assertEquals(sqlDate, mapper.findSqlDate(10L));
        assertEquals(sqlTime.toLocalTime(), mapper.findSqlTime(10L).toLocalTime());
        assertEquals(sqlTimestamp, mapper.findSqlTimestamp(10L));
        assertNull(mapper.findUtilDate(11L));
        assertNull(mapper.findSqlDate(11L));
        assertNull(mapper.findSqlTime(11L));
        assertNull(mapper.findSqlTimestamp(11L));

        java.util.Date dateOnly = new java.util.Date(sqlDate.getTime());
        java.util.Date timeOnly = new java.util.Date(sqlTime.getTime());
        assertEquals(1, mapper.insertLegacyDateOnlyValues(12L, dateOnly, timeOnly));
        assertEquals(1, mapper.insertLegacyDateOnlyValues(13L, null, null));
        assertEquals(sqlDate.toLocalDate(),
            new java.sql.Date(mapper.findUtilDateOnly(12L).getTime()).toLocalDate());
        assertEquals(sqlTime.toLocalTime(),
            new java.sql.Time(mapper.findUtilTimeOnly(12L).getTime()).toLocalTime());
        assertNull(mapper.findUtilDateOnly(13L));
        assertNull(mapper.findUtilTimeOnly(13L));
    }

    @Test
    void mapsCalendarValuesThroughStandardMappings() {
        Year year = Year.of(2026);
        Month month = Month.SEPTEMBER;
        YearMonth yearMonth = YearMonth.of(2026, month);
        JapaneseDate japaneseDate = JapaneseDate.from(LocalDate.of(2026, 9, 6));

        assertEquals(1, mapper.insertCalendarValues(20L, year, month, yearMonth, japaneseDate));
        assertEquals(1, mapper.insertCalendarValues(21L, null, null, null, null));

        assertEquals(year, mapper.findYear(20L));
        assertEquals(month, mapper.findMonth(20L));
        assertEquals(yearMonth, mapper.findYearMonth(20L));
        assertEquals(japaneseDate, mapper.findJapaneseDate(20L));
        assertNull(mapper.findYear(21L));
        assertNull(mapper.findMonth(21L));
        assertNull(mapper.findYearMonth(21L));
        assertNull(mapper.findJapaneseDate(21L));
    }

    @Test
    void mapsEnumNamesByDefaultAndOrdinalsWhenExplicitlyDeclared() {
        assertEquals(1, mapper.insertEnums(30L, StandardStatus.ACTIVE, StandardStatus.DISABLED));
        assertEquals(1, mapper.insertEnums(31L, null, null));

        assertEquals(StandardStatus.ACTIVE, mapper.findEnumName(30L));
        assertEquals(StandardStatus.DISABLED, mapper.findEnumOrdinal(30L));
        assertNull(mapper.findEnumName(31L));
        assertNull(mapper.findEnumOrdinal(31L));
    }

    @Test
    void mapsNationalCharacterStringsThroughThePostgreSqlUnicodeStringContract() {
        String charValue = "Ångström";
        String varcharValue = "Καλημέρα κόσμε";

        assertEquals(1, mapper.insertNationalStrings(40L, charValue, varcharValue));
        assertEquals(1, mapper.insertNationalStrings(41L, null, null));
        assertEquals(charValue, mapper.findNationalChar(40L));
        assertEquals(varcharValue, mapper.findNationalVarchar(40L));
        assertNull(mapper.findNationalChar(41L));
        assertNull(mapper.findNationalVarchar(41L));
    }

    @Test
    void mapsStandardValuesAcrossSupportedExecutionAndResultShapes() {
        BigInteger dynamicValue = new BigInteger("50000000000000000001");
        BigInteger firstBatchValue = new BigInteger("51000000000000000001");
        BigInteger secondBatchValue = new BigInteger("52000000000000000001");
        BigInteger compositeValue = new BigInteger("53000000000000000001");

        assertEquals(1, mapper.insertIntegerDynamic(50L, dynamicValue));
        assertArrayEquals(new int[]{1, 1}, mapper.insertIntegerBatch(java.util.List.of(
            new PostgreSqlStandardTypesMapper.IntegerRow(51L, firstBatchValue),
            new PostgreSqlStandardTypesMapper.IntegerRow(52L, secondBatchValue))));
        assertEquals(1, mapper.insertStandardComposite(
            53L, compositeValue, StandardStatus.DISABLED));

        assertEquals(java.util.List.of(dynamicValue, firstBatchValue, secondBatchValue, compositeValue),
            mapper.findIntegers());
        assertEquals(java.util.Optional.of(dynamicValue), mapper.findOptionalInteger(50L));
        assertEquals(java.util.Optional.empty(), mapper.findOptionalInteger(999L));

        PostgreSqlStandardTypesMapper.StandardValueRecord record = mapper.findStandardRecord(53L);
        assertEquals(compositeValue, record.integerValue());
        assertEquals(StandardStatus.DISABLED, record.enumOrdinalValue());
        PostgreSqlStandardTypesMapper.StandardValueBean bean = mapper.findStandardBean(53L);
        assertEquals(compositeValue, bean.getIntegerValue());
        assertEquals(StandardStatus.DISABLED, bean.getEnumOrdinalValue());

        assertEquals(BigInteger.ONE, mapper.insertGeneratedKey("first"));
    }
}

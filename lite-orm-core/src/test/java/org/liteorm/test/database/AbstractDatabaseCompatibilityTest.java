package org.liteorm.test.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.liteorm.JdbcAssembly;
import org.liteorm.LiteOrm;
import org.liteorm.annotation.Batch;
import org.liteorm.annotation.GeneratedKey;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Result;
import org.liteorm.annotation.Results;
import org.liteorm.annotation.Select;
import org.liteorm.annotation.UseRowMapper;
import org.liteorm.api.CursorCallback;
import org.liteorm.api.ExecutionPhase;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.RowMapper;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.StatementOptions;
import org.liteorm.api.TransactionException;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.chrono.JapaneseDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractDatabaseCompatibilityTest {

    private JdbcAssembly assembly;
    private DatabaseCompatibilityMapper mapper;
    private DataSource dataSource;

    protected abstract DatabaseEngine databaseEngine();

    protected abstract String identityDefinition();

    protected abstract String binaryDefinition();

    protected abstract String uuidDefinition();

    protected abstract String localTimeDefinition();

    protected abstract String offsetDateTimeDefinition();

    protected abstract String nationalCharDefinition();

    protected abstract String nationalVarcharDefinition();

    protected abstract String sleepSql();

    @BeforeEach
    void resetDatabase() throws Exception {
        dataSource = TestDatabase.shared(databaseEngine()).createDataSource();
        executeSchema();
        assembly = LiteOrm.jdbc(dataSource).domain(databaseName()).build();
        mapper = new DatabaseCompatibilityMapperImpl(assembly.sqlExecutor());
    }

    @Test
    void mapsScalarRecordBeanDateTimeIdentifierAndBinaryValues() {
        CompatibilityRecord expected = sample(null, "Alice");
        long id = mapper.insert(
            expected.name(), expected.active(), expected.businessDate(), expected.createdAt(),
            expected.eventId(), expected.uuid(), expected.localTime(), expected.offsetDateTime(), expected.payload());

        assertEquals(1L, id);
        assertEquals(1L, mapper.count());
        assertEquals("Alice", mapper.findName(id));
        assertRecord(expected, mapper.findRecord(id), id);
        assertBean(expected, mapper.findBean(id), id);
        assertEquals(expected.uuid(), mapper.findUuid(id));
        assertEquals(expected.localTime(), mapper.findLocalTime(id));
        assertEquals(expected.offsetDateTime().toInstant(), mapper.findOffsetDateTime(id).toInstant());
    }

    @Test
    void preservesNullFrozenJdbcTypes() {
        CompatibilityRecord expected = sample(null, "NullTypes");
        long id = mapper.insert(
            expected.name(), expected.active(), expected.businessDate(), expected.createdAt(),
            expected.eventId(), null, null, null, expected.payload());

        CompatibilityRecord actual = mapper.findRecord(id);
        assertNull(actual.uuid());
        assertNull(actual.localTime());
        assertNull(actual.offsetDateTime());
        assertNull(mapper.findUuid(id));
        assertNull(mapper.findLocalTime(id));
        assertNull(mapper.findOffsetDateTime(id));
    }

    @Test
    void routesTheFixedCoreStandardTypesInBothDirections() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 8, 7, 8, 9, 123_000_000);
        StandardRouteRecord expected = new StandardRouteRecord(
            new BigInteger("12345678901234567890123456789012345678"),
            new Byte[]{0, 1, 2, 127, -1},
            new java.util.Date(java.sql.Timestamp.valueOf(dateTime).getTime()),
            java.sql.Date.valueOf(dateTime.toLocalDate()),
            java.sql.Time.valueOf(dateTime.toLocalTime()),
            java.sql.Timestamp.valueOf(dateTime),
            Year.of(2026),
            Month.SEPTEMBER,
            YearMonth.of(2026, Month.SEPTEMBER),
            JapaneseDate.from(dateTime.toLocalDate()),
            StandardStatus.ACTIVE,
            StandardStatus.DISABLED,
            "Ångström",
            "Καλημέρα κόσμε");

        assertEquals(1, mapper.insertStandardRoutes(1L, expected));
        assertEquals(1, mapper.insertStandardRoutes(2L, StandardRouteRecord.empty()));

        assertStandardRoutes(expected, mapper.findStandardRoutes(1L));
        assertStandardRoutes(StandardRouteRecord.empty(), mapper.findStandardRoutes(2L));
    }

    @Test
    void executesDynamicSqlAndJdbcBatch() {
        CompatibilityRecord first = sample(100L, "Alice");
        CompatibilityRecord second = sample(101L, "Bob");

        int[] updateCounts = mapper.insertBatch(List.of(first, second));

        assertEquals(2, updateCounts.length);
        for (int updateCount : updateCounts) {
            assertTrue(updateCount == 1 || updateCount == Statement.SUCCESS_NO_INFO);
        }
        assertEquals(List.of("Alice"), mapper.searchNames("Ali%", true));
        assertEquals(List.of("Alice", "Bob"), mapper.searchNames(null, true));
    }

    @Test
    void commitsAndRollsBackGeneratedMapperWork() {
        long committedId = assembly.transactionalExecutor().execute(() -> {
            CompatibilityRecord value = sample(null, "Committed");
            return mapper.insert(value.name(), value.active(), value.businessDate(), value.createdAt(),
                value.eventId(), value.uuid(), value.localTime(), value.offsetDateTime(), value.payload());
        });
        assertEquals("Committed", mapper.findName(committedId));

        assertThrows(IllegalStateException.class, () ->
            assembly.transactionalExecutor().execute(() -> {
                CompatibilityRecord value = sample(null, "RolledBack");
                mapper.insert(value.name(), value.active(), value.businessDate(), value.createdAt(),
                    value.eventId(), value.uuid(), value.localTime(), value.offsetDateTime(), value.payload());
                throw new IllegalStateException("rollback");
            }));

        assertEquals(List.of("Committed"), mapper.searchNames(null, true));
    }

    @Test
    void nestedFailureMarksRootTransactionRollbackOnly() {
        TransactionException failure = assertThrows(TransactionException.class, () ->
            assembly.transactionalExecutor().execute(() -> {
                CompatibilityRecord outer = sample(null, "Outer");
                mapper.insert(outer.name(), outer.active(), outer.businessDate(), outer.createdAt(),
                    outer.eventId(), outer.uuid(), outer.localTime(), outer.offsetDateTime(), outer.payload());
                try {
                    assembly.transactionalExecutor().execute(() -> {
                        CompatibilityRecord nested = sample(null, "Nested");
                        mapper.insert(nested.name(), nested.active(), nested.businessDate(), nested.createdAt(),
                            nested.eventId(), nested.uuid(), nested.localTime(), nested.offsetDateTime(), nested.payload());
                        throw new IllegalArgumentException("nested failure");
                    });
                } catch (IllegalArgumentException ignored) {
                }
                return null;
            }));

        assertEquals(TransactionException.Type.ROLLBACK_ONLY, failure.getType());
        assertEquals(0L, mapper.count());
    }

    @Test
    void consumesRowsThroughScopeBoundCursor() {
        CompatibilityRecord first = sample(200L, "Alice");
        CompatibilityRecord second = sample(201L, "Bob");
        mapper.insertBatch(List.of(first, second));

        List<String> names = mapper.scan(200L, cursor -> {
            List<String> result = new ArrayList<>();
            while (cursor.next()) {
                result.add(cursor.current().name());
            }
            return result;
        });

        assertEquals(List.of("Alice", "Bob"), names);
    }

    @Test
    void enforcesJdbcQueryTimeout() {
        ExecutionPlan timeoutPlan = new ExecutionPlan(
            getClass().getName() + ".timeout",
            sleepSql(),
            new Object[0],
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.GENERATED,
            null,
            null,
            null,
            new StatementOptions(1, null, null));

        SqlExecutionException failure = assertThrows(
            SqlExecutionException.class, () -> assembly.sqlExecutor().execute(timeoutPlan));

        assertEquals(ExecutionPhase.EXECUTION, failure.getPhase());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failure.getExecutionState());
    }

    private void executeSchema() throws SQLException, IOException {
        String schema;
        try (InputStream input = getClass().getResourceAsStream("/database/schema.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing database/schema.sql");
            }
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                .replace("${identity}", identityDefinition())
                .replace("${binary}", binaryDefinition())
                .replace("${uuid}", uuidDefinition())
                .replace("${localTime}", localTimeDefinition())
                .replace("${offsetDateTime}", offsetDateTimeDefinition())
                .replace("${nationalChar}", nationalCharDefinition())
                .replace("${nationalVarchar}", nationalVarcharDefinition());
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql.trim());
                }
            }
        }
    }

    private CompatibilityRecord sample(Long id, String name) {
        return new CompatibilityRecord(
            id,
            name,
            true,
            LocalDate.of(2026, 8, 16),
            LocalDateTime.of(2026, 8, 16, 12, 34, 56, 123_456_000),
            UUID.nameUUIDFromBytes((databaseName() + name).getBytes(StandardCharsets.UTF_8)).toString(),
            UUID.nameUUIDFromBytes((databaseName() + name + "uuid").getBytes(StandardCharsets.UTF_8)),
            LocalTime.of(12, 34, 56, 123_456_000),
            OffsetDateTime.of(2026, 8, 16, 12, 34, 56, 654_321_000, ZoneOffset.ofHours(8)),
            new byte[]{1, 2, 3, 4});
    }

    private void assertRecord(CompatibilityRecord expected, CompatibilityRecord actual, long id) {
        assertEquals(id, actual.id());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.active(), actual.active());
        assertEquals(expected.businessDate(), actual.businessDate());
        assertEquals(expected.createdAt(), actual.createdAt());
        assertEquals(expected.eventId(), actual.eventId());
        assertEquals(expected.uuid(), actual.uuid());
        assertEquals(expected.localTime(), actual.localTime());
        assertEquals(expected.offsetDateTime().toInstant(), actual.offsetDateTime().toInstant());
        assertArrayEquals(expected.payload(), actual.payload());
    }

    private void assertBean(CompatibilityRecord expected, CompatibilityBean actual, long id) {
        assertEquals(id, actual.getId());
        assertEquals(expected.name(), actual.getName());
        assertEquals(expected.active(), actual.getActive());
        assertEquals(expected.businessDate(), actual.getBusinessDate());
        assertEquals(expected.createdAt(), actual.getCreatedAt());
        assertEquals(expected.eventId(), actual.getEventId());
        assertEquals(expected.uuid(), actual.getUuid());
        assertEquals(expected.localTime(), actual.getLocalTime());
        assertEquals(expected.offsetDateTime().toInstant(), actual.getOffsetDateTime().toInstant());
        assertArrayEquals(expected.payload(), actual.getPayload());
    }

    private void assertStandardRoutes(StandardRouteRecord expected, StandardRouteRecord actual) {
        assertEquals(expected.integerValue(), actual.integerValue());
        assertArrayEquals(expected.binaryValue(), actual.binaryValue());
        assertEquals(expected.utilDateValue(), actual.utilDateValue());
        assertEquals(expected.sqlDateValue(), actual.sqlDateValue());
        if (expected.sqlTimeValue() == null) {
            assertNull(actual.sqlTimeValue());
        } else {
            assertEquals(expected.sqlTimeValue().toLocalTime(), actual.sqlTimeValue().toLocalTime());
        }
        assertEquals(expected.sqlTimestampValue(), actual.sqlTimestampValue());
        assertEquals(expected.yearValue(), actual.yearValue());
        assertEquals(expected.monthValue(), actual.monthValue());
        assertEquals(expected.yearMonthValue(), actual.yearMonthValue());
        assertEquals(expected.japaneseDateValue(), actual.japaneseDateValue());
        assertEquals(expected.enumNameValue(), actual.enumNameValue());
        assertEquals(expected.enumOrdinalValue(), actual.enumOrdinalValue());
        assertEquals(expected.nationalCharValue(), actual.nationalCharValue());
        assertEquals(expected.nationalVarcharValue(), actual.nationalVarcharValue());
    }

    private String databaseName() {
        return getClass().getSimpleName();
    }
}

@Mapper
interface DatabaseCompatibilityMapper {

    String COLUMNS = "id, name, active, business_date, created_at, event_id, "
        + "uuid_value, local_time_value, offset_date_time_value, payload";

    @GeneratedKey("id")
    @Insert("INSERT INTO compatibility_users (name, active, business_date, created_at, event_id, "
        + "uuid_value, local_time_value, offset_date_time_value, payload) "
        + "VALUES (#{name}, #{active}, #{businessDate}, #{createdAt}, #{eventId}, #{uuid,jdbcType=VARCHAR}, #{localTime}, "
        + "#{offsetDateTime}, #{payload})")
    Long insert(
        @Param("name") String name,
        @Param("active") Boolean active,
        @Param("businessDate") LocalDate businessDate,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("eventId") String eventId,
        @Param("uuid") UUID uuid,
        @Param("localTime") LocalTime localTime,
        @Param("offsetDateTime") OffsetDateTime offsetDateTime,
        @Param("payload") byte[] payload);

    @Batch("INSERT INTO compatibility_users (id, name, active, business_date, created_at, event_id, "
        + "uuid_value, local_time_value, offset_date_time_value, payload) "
        + "VALUES (#{item.id}, #{item.name}, #{item.active}, #{item.businessDate}, #{item.createdAt}, "
        + "#{item.eventId}, #{item.uuid,jdbcType=VARCHAR}, #{item.localTime}, #{item.offsetDateTime}, #{item.payload})")
    int[] insertBatch(List<CompatibilityRecord> values);

    @Select("SELECT COUNT(*) FROM compatibility_users")
    Long count();

    @Select("SELECT name FROM compatibility_users WHERE id = #{id}")
    String findName(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM compatibility_users WHERE id = #{id}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "name", column = "name"),
        @Result(property = "active", column = "active"),
        @Result(property = "businessDate", column = "business_date"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "eventId", column = "event_id"),
        @Result(property = "uuid", column = "uuid_value"),
        @Result(property = "localTime", column = "local_time_value"),
        @Result(property = "offsetDateTime", column = "offset_date_time_value"),
        @Result(property = "payload", column = "payload")
    })
    CompatibilityRecord findRecord(@Param("id") long id);

    @Select("SELECT " + COLUMNS + " FROM compatibility_users WHERE id = #{id}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "name", column = "name"),
        @Result(property = "active", column = "active"),
        @Result(property = "businessDate", column = "business_date"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "eventId", column = "event_id"),
        @Result(property = "uuid", column = "uuid_value"),
        @Result(property = "localTime", column = "local_time_value"),
        @Result(property = "offsetDateTime", column = "offset_date_time_value"),
        @Result(property = "payload", column = "payload")
    })
    CompatibilityBean findBean(@Param("id") long id);

    @Select("SELECT uuid_value FROM compatibility_users WHERE id = #{id}")
    UUID findUuid(@Param("id") long id);

    @Select("SELECT local_time_value FROM compatibility_users WHERE id = #{id}")
    LocalTime findLocalTime(@Param("id") long id);

    @Select("SELECT offset_date_time_value FROM compatibility_users WHERE id = #{id}")
    OffsetDateTime findOffsetDateTime(@Param("id") long id);

    @Insert("INSERT INTO standard_type_values (id, integer_value, binary_value, util_date_value, "
        + "sql_date_value, sql_time_value, sql_timestamp_value, year_value, month_value, "
        + "year_month_value, japanese_date_value, enum_name_value, enum_ordinal_value, "
        + "national_char_value, national_varchar_value) VALUES (#{id}, #{value.integerValue}, "
        + "#{value.binaryValue}, #{value.utilDateValue}, #{value.sqlDateValue}, #{value.sqlTimeValue}, "
        + "#{value.sqlTimestampValue}, #{value.yearValue}, #{value.monthValue}, #{value.yearMonthValue}, "
        + "#{value.japaneseDateValue}, #{value.enumNameValue}, "
        + "#{value.enumOrdinalValue,jdbcType=INTEGER}, #{value.nationalCharValue,jdbcType=VARCHAR}, "
        + "#{value.nationalVarcharValue,jdbcType=VARCHAR})")
    int insertStandardRoutes(
        @Param("id") long id,
        @Param("value") StandardRouteRecord value);

    @Select("SELECT integer_value AS integerValue, binary_value AS binaryValue, "
        + "util_date_value AS utilDateValue, sql_date_value AS sqlDateValue, "
        + "sql_time_value AS sqlTimeValue, sql_timestamp_value AS sqlTimestampValue, "
        + "year_value AS yearValue, month_value AS monthValue, year_month_value AS yearMonthValue, "
        + "japanese_date_value AS japaneseDateValue, enum_name_value AS enumNameValue, "
        + "enum_ordinal_value AS enumOrdinalValue, national_char_value AS nationalCharValue, "
        + "national_varchar_value AS nationalVarcharValue FROM standard_type_values WHERE id = #{id}")
    StandardRouteRecord findStandardRoutes(@Param("id") long id);

    @Select({
        "<script>",
        "SELECT name FROM compatibility_users",
        "<where>",
        "<if test=\"namePattern != null and namePattern != ''\">name LIKE #{namePattern}</if>",
        "<if test=\"active != null\">AND active = #{active}</if>",
        "</where>",
        "ORDER BY id",
        "</script>"
    })
    List<String> searchNames(@Param("namePattern") String namePattern, @Param("active") Boolean active);

    @Select("SELECT " + COLUMNS + " FROM compatibility_users WHERE id >= #{minimumId} ORDER BY id")
    @UseRowMapper(CompatibilityRecordRowMapper.class)
    List<String> scan(long minimumId, CursorCallback<CompatibilityRecord, List<String>> callback);
}

record CompatibilityRecord(
    Long id,
    String name,
    Boolean active,
    LocalDate businessDate,
    LocalDateTime createdAt,
    String eventId,
    UUID uuid,
    LocalTime localTime,
    OffsetDateTime offsetDateTime,
    byte[] payload) {
}

record StandardRouteRecord(
    BigInteger integerValue,
    Byte[] binaryValue,
    java.util.Date utilDateValue,
    java.sql.Date sqlDateValue,
    java.sql.Time sqlTimeValue,
    java.sql.Timestamp sqlTimestampValue,
    Year yearValue,
    Month monthValue,
    YearMonth yearMonthValue,
    JapaneseDate japaneseDateValue,
    StandardStatus enumNameValue,
    StandardStatus enumOrdinalValue,
    String nationalCharValue,
    String nationalVarcharValue) {

    static StandardRouteRecord empty() {
        return new StandardRouteRecord(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

enum StandardStatus {
    ACTIVE,
    DISABLED
}

final class CompatibilityBean {

    private Long id;
    private String name;
    private Boolean active;
    private LocalDate businessDate;
    private LocalDateTime createdAt;
    private String eventId;
    private UUID uuid;
    private LocalTime localTime;
    private OffsetDateTime offsetDateTime;
    private byte[] payload;

    public CompatibilityBean() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public LocalTime getLocalTime() { return localTime; }
    public void setLocalTime(LocalTime localTime) { this.localTime = localTime; }
    public OffsetDateTime getOffsetDateTime() { return offsetDateTime; }
    public void setOffsetDateTime(OffsetDateTime offsetDateTime) { this.offsetDateTime = offsetDateTime; }
    public byte[] getPayload() { return payload; }
    public void setPayload(byte[] payload) { this.payload = payload; }
}

final class CompatibilityRecordRowMapper implements RowMapper<CompatibilityRecord> {

    public CompatibilityRecordRowMapper() {
    }

    @Override
    public CompatibilityRecord map(ResultSet resultSet) throws SQLException {
        return new CompatibilityRecord(
            resultSet.getLong("id"),
            resultSet.getString("name"),
            resultSet.getBoolean("active"),
            resultSet.getObject("business_date", LocalDate.class),
            resultSet.getObject("created_at", LocalDateTime.class),
            resultSet.getString("event_id"),
            UUID.fromString(resultSet.getString("uuid_value")),
            resultSet.getObject("local_time_value", LocalTime.class),
            resultSet.getObject("offset_date_time_value", OffsetDateTime.class),
            resultSet.getBytes("payload"));
    }
}

package org.liteorm.types.postgresql.fixture;

import org.liteorm.annotation.Column;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PostgreSqlTypesRecord(
    Long id,
    @Column("uuid_value") UUID uuid,
    @Column("local_time_value") LocalTime localTime,
    @Column("offset_date_time_value") OffsetDateTime offsetDateTime
) {
}

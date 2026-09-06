package org.liteorm.types.mysql.fixture;

import org.liteorm.annotation.Column;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class MySqlTypesBean {

    private Long id;
    @Column("uuid_value")
    private UUID uuid;
    @Column("local_time_value")
    private LocalTime localTime;
    @Column("offset_date_time_value")
    private OffsetDateTime offsetDateTime;

    public MySqlTypesBean() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(LocalTime localTime) {
        this.localTime = localTime;
    }

    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
        this.offsetDateTime = offsetDateTime;
    }
}

package org.liteorm.example;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

enum JdbcStatus {
    ACTIVE,
    DISABLED
}

record JdbcTypesRow(
    Long id,
    Integer quantity,
    BigDecimal amount,
    LocalDate businessDate,
    LocalDateTime createdAt,
    Instant occurredAt,
    JdbcStatus status,
    byte[] payload,
    Boolean enabled,
    long requiredCount,
    int requiredQuantity,
    short smallValue,
    byte tinyValue,
    double ratio,
    float score,
    boolean requiredEnabled,
    char code
) {
}

@Mapper
interface JdbcTypeMapperFixture {

    @Select("SELECT id, quantity, amount, business_date, created_at, occurred_at, status, payload, enabled, "
        + "required_count, required_quantity, small_value, tiny_value, ratio, score, required_enabled, code "
        + "FROM jdbc_types WHERE id = #{id}")
    JdbcTypesRow findById(@Param("id") Long id);
}

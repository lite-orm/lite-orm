package org.liteorm.types.postgresql.fixture;

import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PostgreSqlTypesMapper {

    @Insert("INSERT INTO postgresql_type_values (id, uuid_value) VALUES (#{id}, #{value})")
    int insertUuid(@Param("id") long id, @Param("value") UUID value);

    @Select("SELECT uuid_value FROM postgresql_type_values WHERE id = #{id}")
    UUID findUuid(@Param("id") long id);

    @Select("SELECT uuid_value FROM postgresql_type_values ORDER BY id")
    List<UUID> findUuids();

    @Select("SELECT uuid_value FROM postgresql_type_values WHERE id = #{id}")
    Optional<UUID> findOptionalUuid(@Param("id") long id);

    @Insert("INSERT INTO postgresql_type_values (id, local_time_value) VALUES (#{id}, #{value})")
    int insertLocalTime(@Param("id") long id, @Param("value") LocalTime value);

    @Select("SELECT local_time_value FROM postgresql_type_values WHERE id = #{id}")
    LocalTime findLocalTime(@Param("id") long id);

    @Select("SELECT local_time_value FROM postgresql_type_values ORDER BY id")
    List<LocalTime> findLocalTimes();

    @Select("SELECT local_time_value FROM postgresql_type_values WHERE id = #{id}")
    Optional<LocalTime> findOptionalLocalTime(@Param("id") long id);

    @Insert("INSERT INTO postgresql_type_values (id, offset_date_time_value) VALUES (#{id}, #{value})")
    int insertOffsetDateTime(@Param("id") long id, @Param("value") OffsetDateTime value);

    @Select("SELECT offset_date_time_value FROM postgresql_type_values WHERE id = #{id}")
    OffsetDateTime findOffsetDateTime(@Param("id") long id);

    @Select("SELECT offset_date_time_value FROM postgresql_type_values ORDER BY id")
    List<OffsetDateTime> findOffsetDateTimes();

    @Select("SELECT offset_date_time_value FROM postgresql_type_values WHERE id = #{id}")
    Optional<OffsetDateTime> findOptionalOffsetDateTime(@Param("id") long id);

    @Insert("INSERT INTO postgresql_type_values "
        + "(id, uuid_value, local_time_value, offset_date_time_value) "
        + "VALUES (#{id}, #{uuid}, #{localTime}, #{offsetDateTime})")
    int insertValues(
        @Param("id") long id,
        @Param("uuid") UUID uuid,
        @Param("localTime") LocalTime localTime,
        @Param("offsetDateTime") OffsetDateTime offsetDateTime
    );

    @Select("SELECT id, uuid_value, local_time_value, offset_date_time_value "
        + "FROM postgresql_type_values WHERE id = #{id}")
    PostgreSqlTypesRecord findRecord(@Param("id") long id);

    @Select("SELECT id, uuid_value, local_time_value, offset_date_time_value "
        + "FROM postgresql_type_values WHERE id = #{id}")
    PostgreSqlTypesBean findBean(@Param("id") long id);
}

package org.liteorm.example.postgresqltypes;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

import java.util.UUID;

@Mapper
interface PostgreSqlTypesArtifactMapper {

    @Select("SELECT uuid_value FROM values WHERE id = #{id}")
    UUID findUuid(@Param("id") long id);
}

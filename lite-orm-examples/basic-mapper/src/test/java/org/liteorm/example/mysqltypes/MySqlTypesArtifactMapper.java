package org.liteorm.example.mysqltypes;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

import java.util.UUID;

@Mapper
interface MySqlTypesArtifactMapper {

    @Select("SELECT uuid_value FROM values_table WHERE id = #{id}")
    UUID findUuid(@Param("id") long id);
}

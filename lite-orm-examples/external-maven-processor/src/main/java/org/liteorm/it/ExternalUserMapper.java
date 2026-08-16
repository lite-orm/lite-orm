package org.liteorm.it;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface ExternalUserMapper {

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    ExternalUser findById(@Param("id") Long id);
}

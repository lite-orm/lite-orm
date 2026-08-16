package org.liteorm.spring.boot.fixture;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface URLMapper {

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

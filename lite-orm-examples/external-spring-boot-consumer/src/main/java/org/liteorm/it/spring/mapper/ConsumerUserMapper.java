package org.liteorm.it.spring.mapper;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface ConsumerUserMapper {

    @Select("select id, name from users where id = #{id}")
    ConsumerUser findById(@Param("id") Long id);
}

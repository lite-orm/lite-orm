package io.github.lynxus.it.spring.mapper;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface ConsumerUserMapper {

    @Select("select id, name from users where id = #{id}")
    ConsumerUser findById(@Param("id") Long id);
}

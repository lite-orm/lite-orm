package io.github.kervix.it.spring.mapper;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface ConsumerUserMapper {

    @Select("select id, name from users where id = #{id}")
    ConsumerUser findById(@Param("id") Long id);
}

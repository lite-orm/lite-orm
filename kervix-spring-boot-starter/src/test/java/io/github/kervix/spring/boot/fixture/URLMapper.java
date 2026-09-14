package io.github.kervix.spring.boot.fixture;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface URLMapper {

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

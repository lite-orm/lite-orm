package io.github.lynxus.spring.boot.fixture;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface URLMapper {

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

package io.github.lynxus.it;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface ExternalUserMapper {

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    ExternalUser findById(@Param("id") Long id);
}

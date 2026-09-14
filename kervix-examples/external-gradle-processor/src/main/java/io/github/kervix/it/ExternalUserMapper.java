package io.github.kervix.it;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface ExternalUserMapper {

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    ExternalUser findById(@Param("id") Long id);
}

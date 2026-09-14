package io.github.lynxus.example.multidatasource.user;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface UserMapper {

    @Select("select id, name from users where id = #{id}")
    User findById(@Param("id") long id);
}

package io.github.kervix.example.multidatasource.user;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface UserMapper {

    @Select("select id, name from users where id = #{id}")
    User findById(@Param("id") long id);
}

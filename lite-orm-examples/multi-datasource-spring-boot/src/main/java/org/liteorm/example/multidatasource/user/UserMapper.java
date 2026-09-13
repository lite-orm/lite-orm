package org.liteorm.example.multidatasource.user;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface UserMapper {

    @Select("select id, name from users where id = #{id}")
    User findById(@Param("id") long id);
}

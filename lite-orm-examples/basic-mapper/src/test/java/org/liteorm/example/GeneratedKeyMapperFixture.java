package org.liteorm.example;

import org.liteorm.annotation.GeneratedKey;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;

@Mapper
interface GeneratedKeyMapperFixture {

    @GeneratedKey
    @Insert("INSERT INTO users (name, email, age) VALUES (#{name}, #{email}, #{age})")
    Long insert(
        @Param("name") String name,
        @Param("email") String email,
        @Param("age") Integer age
    );
}

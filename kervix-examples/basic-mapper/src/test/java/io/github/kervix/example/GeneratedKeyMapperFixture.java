package io.github.kervix.example;

import io.github.kervix.annotation.GeneratedKey;
import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;

@Mapper
interface GeneratedKeyMapperFixture {

    @GeneratedKey("id")
    @Insert("INSERT INTO users (name, email, age) VALUES (#{name}, #{email}, #{age})")
    Long insert(
        @Param("name") String name,
        @Param("email") String email,
        @Param("age") Integer age
    );
}

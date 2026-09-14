package io.github.lynxus.example;

import io.github.lynxus.annotation.GeneratedKey;
import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;

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

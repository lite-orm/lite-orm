package io.github.lynxus.example;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Select;

@Mapper
interface SingleResultMapperFixture {

    @Select("SELECT name FROM users ORDER BY id")
    String findName();
}

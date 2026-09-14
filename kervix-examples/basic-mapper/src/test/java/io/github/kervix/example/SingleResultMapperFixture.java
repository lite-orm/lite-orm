package io.github.kervix.example;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Select;

@Mapper
interface SingleResultMapperFixture {

    @Select("SELECT name FROM users ORDER BY id")
    String findName();
}

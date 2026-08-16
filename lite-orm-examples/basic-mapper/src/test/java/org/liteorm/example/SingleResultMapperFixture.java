package org.liteorm.example;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Select;

@Mapper
interface SingleResultMapperFixture {

    @Select("SELECT name FROM users ORDER BY id")
    String findName();
}

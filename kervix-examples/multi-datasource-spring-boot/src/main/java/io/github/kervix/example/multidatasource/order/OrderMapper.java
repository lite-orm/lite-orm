package io.github.kervix.example.multidatasource.order;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface OrderMapper {

    @Select("select id, user_id from orders where id = #{id}")
    Order findById(@Param("id") long id);
}

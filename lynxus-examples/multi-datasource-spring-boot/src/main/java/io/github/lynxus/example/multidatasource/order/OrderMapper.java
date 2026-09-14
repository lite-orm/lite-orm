package io.github.lynxus.example.multidatasource.order;

import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;

@Mapper
public interface OrderMapper {

    @Select("select id, user_id from orders where id = #{id}")
    Order findById(@Param("id") long id);
}

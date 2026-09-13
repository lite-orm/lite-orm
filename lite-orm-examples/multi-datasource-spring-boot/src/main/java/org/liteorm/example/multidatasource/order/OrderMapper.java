package org.liteorm.example.multidatasource.order;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface OrderMapper {

    @Select("select id, user_id from orders where id = #{id}")
    Order findById(@Param("id") long id);
}

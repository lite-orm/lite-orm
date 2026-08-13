package org.liteorm.example;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;

import java.util.List;

@Mapper
public interface UserXmlMapper {

    User findByEmail(@Param("email") String email);

    List<User> findByIds(@Param("ids") List<Long> ids);

    int updateSelective(@Param("user") User user);
}

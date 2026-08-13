package org.liteorm.example;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserXmlMapper {

    User findByEmail(@Param("email") String email);

    List<User> findByIds(@Param("ids") List<Long> ids);

    int updateSelective(@Param("user") User user);
}

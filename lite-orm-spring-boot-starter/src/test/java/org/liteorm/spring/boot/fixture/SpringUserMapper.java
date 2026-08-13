package org.liteorm.spring.boot.fixture;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpringUserMapper {

    @Insert("INSERT INTO spring_users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

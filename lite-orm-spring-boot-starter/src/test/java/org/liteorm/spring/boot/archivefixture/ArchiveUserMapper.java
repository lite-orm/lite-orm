package org.liteorm.spring.boot.archivefixture;

import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;
import org.liteorm.spring.boot.fixture.SpringUser;

@Mapper
public interface ArchiveUserMapper {

    @Insert("INSERT INTO spring_users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

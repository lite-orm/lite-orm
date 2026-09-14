package io.github.kervix.spring.boot.archivefixture;

import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;
import io.github.kervix.spring.boot.fixture.SpringUser;

@Mapper
public interface ArchiveUserMapper {

    @Insert("INSERT INTO spring_users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

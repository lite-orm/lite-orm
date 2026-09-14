package io.github.lynxus.spring.boot.archivefixture;

import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;
import io.github.lynxus.spring.boot.fixture.SpringUser;

@Mapper
public interface ArchiveUserMapper {

    @Insert("INSERT INTO spring_users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM spring_users WHERE id = #{id}")
    SpringUser findById(@Param("id") Long id);
}

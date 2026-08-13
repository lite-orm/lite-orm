package org.liteorm.example;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.liteorm.annotation.UseSqlProvider;
import org.liteorm.api.ExecutionPlan;

import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO users (id, name, email, age) VALUES (#{id}, #{name}, #{email}, #{age})")
    int insert(@Param("id") Long id, @Param("name") String name, @Param("email") String email,
               @Param("age") Integer age);

    @Select("SELECT id, name, email, age FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Update("UPDATE users SET name = #{name}, email = #{email}, age = #{age} WHERE id = #{id}")
    int update(@Param("id") Long id, @Param("name") String name, @Param("email") String email,
               @Param("age") Integer age);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT name FROM users WHERE id = #{id}")
    String findNameById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM users")
    Long countUsers();

    @Select("SELECT name FROM users ORDER BY id")
    List<String> findAllNames();

    @Select("SELECT id, name, email, age FROM users WHERE id = #{id}")
    UserBean findBeanById(@Param("id") Long id);

    @Select("SELECT id, name, email, age FROM users ORDER BY id")
    List<UserBean> findAllBeans();

    @UseSqlProvider(value = UserSearchProvider.class, statementType = ExecutionPlan.StatementType.SELECT)
    List<User> search(UserSearch search);
}

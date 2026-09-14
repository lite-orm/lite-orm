package io.github.lynxus.example;

import io.github.lynxus.annotation.Delete;
import io.github.lynxus.annotation.Batch;
import io.github.lynxus.annotation.Insert;
import io.github.lynxus.annotation.Mapper;
import io.github.lynxus.annotation.Param;
import io.github.lynxus.annotation.Select;
import io.github.lynxus.annotation.Update;
import io.github.lynxus.annotation.UseSqlProvider;
import io.github.lynxus.api.ExecutionPlan;

import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO users (id, name, email, age) VALUES (#{id}, #{name}, #{email}, #{age})")
    int insert(@Param("id") Long id, @Param("name") String name, @Param("email") String email,
               @Param("age") Integer age);

    @Batch("INSERT INTO users (id, name, email, age) VALUES (#{item.id}, #{item.name}, #{item.email}, #{item.age})")
    int[] insertBatch(List<User> users);

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

package io.github.kervix.test;

import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Select;
import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Update;
import io.github.kervix.annotation.Delete;

import java.util.List;

/**
 * Mapper fixture used by generated-source tests.
 * 
 * @author kervix
 * @since 2024/09/29
 */
@Mapper
public interface UserMapper {
    
    /**
     * Tests single-object mapping and parameter binding.
     */
    @Select("SELECT id, name, email, age FROM users WHERE id = #{id}")
    User findById(Long id);
    
    /**
     * Tests list mapping.
     */
    @Select("SELECT id, name, email, age FROM users")
    List<User> findAll();
    
    /**
     * Tests named parameter binding.
     */
    @Select("SELECT id, name, email, age FROM users WHERE name LIKE #{name}")
    List<User> findByName(String name);
    
    /**
     * Tests multiple insert parameters.
     */
    @Insert("INSERT INTO users (name, email, age) VALUES (#{name}, #{email}, #{age})")
    int insert(String name, String email, Integer age);
    
    /**
     * Tests multiple update parameters.
     */
    @Update("UPDATE users SET name = #{name}, email = #{email}, age = #{age} WHERE id = #{id}")
    int update(Long id, String name, String email, Integer age);

    @Update("UPDATE users SET age = #{age} WHERE id = #{id}")
    long updateAge(Long id, Integer age);
    
    /**
     * Tests a simple delete parameter.
     */
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);
    
    /**
     * Tests nested parameter property access.
     */
    @Select("SELECT id, name, email, age FROM users WHERE name = #{user.name} AND age = #{user.age}")
    List<User> findByUser(User user);
    
    /**
     * Tests an annotation dynamic SQL script.
     */
    @Select({
        "<script>",
        "SELECT id, name, email, age FROM users WHERE 1=1",
        "<if test='name != null'>AND name LIKE #{name}</if>",
        "<if test='age != null'>AND age = #{age}</if>",
        "</script>"
    })
    List<User> findByCondition(String name, Integer age);
}

package org.liteorm.test;

import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Select;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Update;
import org.liteorm.annotation.Delete;

import java.util.List;

/**
 * 用户Mapper接口 - 用于测试代码生成
 * 
 * 基于物理必需性：
 * - 每个方法对应一个SQL操作
 * - 返回类型明确：User, List<User>, int
 * - 参数类型安全：Long, String等基础类型
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
@Mapper
public interface UserMapper {
    
    /**
     * 根据ID查询用户 - 测试单对象映射和#{param}语法
     */
    @Select("SELECT id, name, email, age FROM users WHERE id = #{id}")
    User findById(Long id);
    
    /**
     * 查询所有用户 - 测试List映射
     */
    @Select("SELECT id, name, email, age FROM users")
    List<User> findAll();
    
    /**
     * 根据名称查询用户 - 测试#{param}参数绑定
     */
    @Select("SELECT id, name, email, age FROM users WHERE name LIKE #{name}")
    List<User> findByName(String name);
    
    /**
     * 插入用户 - 测试多个#{param}参数
     */
    @Insert("INSERT INTO users (name, email, age) VALUES (#{name}, #{email}, #{age})")
    int insert(String name, String email, Integer age);
    
    /**
     * 更新用户 - 测试多个#{param}参数
     */
    @Update("UPDATE users SET name = #{name}, email = #{email}, age = #{age} WHERE id = #{id}")
    int update(Long id, String name, String email, Integer age);
    
    /**
     * 删除用户 - 测试简单#{param}
     */
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);
    
    /**
     * 根据用户对象查询 - 测试对象属性访问#{user.name}
     */
    @Select("SELECT id, name, email, age FROM users WHERE name = #{user.name} AND age = #{user.age}")
    List<User> findByUser(User user);
    
    /**
     * 动态SQL示例 - 测试<script>标签
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

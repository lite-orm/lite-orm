package org.liteorm.test;

/**
 * 用户实体 - 使用record class（零反射映射目标）
 * 
 * 基于物理必需性：
 * - record class提供不可变数据容器
 * - 编译期生成所有getter方法
 * - 零反射开销的构造器
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public record User(
    Long id,
    String name, 
    String email,
    Integer age
) {
    // record class自动生成：
    // - 构造器：User(Long id, String name, String email, Integer age)
    // - getter方法：id(), name(), email(), age()
    // - equals(), hashCode(), toString()
    
    /**
     * 从Object[]创建User的硬编码工厂方法
     * 这将被代码生成器生成，避免反射
     */
    public static User fromResultSet(Object[] row) {
        return new User(
            (Long) row[0],      // id - 硬编码位置
            (String) row[1],    // name - 硬编码位置  
            (String) row[2],    // email - 硬编码位置
            (Integer) row[3]    // age - 硬编码位置
        );
    }
}

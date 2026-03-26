package org.liteorm.test;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

/**
 * 复杂SQL Mapper - 测试MyBatis高级特性
 * 
 * 测试目标：
 * 1. 多层嵌套动态SQL
 * 2. SQL片段引用和复用
 * 3. 复杂foreach批量操作
 * 4. 动态表名/字段名
 * 5. ResultMap复杂映射
 * 6. 一对多/多对一关联查询
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
@Mapper
public interface ComplexSqlMapper {
    
    /**
     * 多层嵌套条件查询
     */
    List<User> findByComplexConditions(
        String name, 
        Integer minAge, 
        Integer maxAge,
        List<String> statusList,
        Boolean includeInactive
    );
    
    /**
     * SQL片段引用测试
     */
    List<User> findUsersWithFragment(String keyword);
    
    /**
     * 复杂批量插入
     */
    int batchInsertOrders(List<Order> orders);
    
    /**
     * 动态表名查询
     */
    List<Map<String, Object>> queryDynamicTable(String tableName, String condition);
    
    /**
     * 多表关联查询 - 一对多
     */
    List<UserWithOrders> findUsersWithOrders(Long userId);
    
    /**
     * 嵌套choose/when/otherwise
     */
    List<User> findByStrategy(String strategy, String value);
    
    /**
     * 复杂trim标签
     */
    int updateUserSelective(User user);
    
    /**
     * bind变量测试
     */
    List<User> searchUsers(String keyword);
    
    /**
     * foreach多种集合类型
     */
    List<User> findByIds(Long[] ids);
    List<Order> findOrdersByIds(List<Long> ids);
    
    /**
     * 嵌套if条件
     */
    List<User> findByNestedConditions(
        String name, 
        String email, 
        Integer age,
        Boolean exactMatch
    );
}

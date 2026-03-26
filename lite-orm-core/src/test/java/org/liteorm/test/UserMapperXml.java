package org.liteorm.test;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 用户Mapper接口 - 纯XML配置方式
 * 用于测试XML解析和代码生成
 * 
 * 物理必需性：
 * - 所有SQL定义在UserMapperXml.xml中
 * - 编译期解析XML生成硬编码实现
 * - 零反射的运行时执行
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
@Mapper
public interface UserMapperXml {
    
    /**
     * 根据ID查询用户 - XML简单查询
     */
    User findById(Long id);
    
    /**
     * 条件查询 - XML动态where/if/choose
     */
    List<User> findByCondition(String name, Integer age, String status);
    
    /**
     * 动态插入 - XML trim标签
     */
    int insertUser(User user);
    
    /**
     * 动态更新 - XML set标签
     */
    int updateUser(User user);
    
    /**
     * 批量插入 - XML foreach标签
     */
    int batchInsert(List<User> list);
    
    /**
     * 分页复杂查询 - XML多条件动态SQL
     */
    List<User> findUsersWithPagination(
        String keyword, 
        Integer minAge, 
        Integer maxAge, 
        List<String> statusList,
        Integer offset,
        Integer limit
    );
}

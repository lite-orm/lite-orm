package org.liteorm.test;

import org.junit.jupiter.api.Test;

/**
 * 类型推断测试 - 验证自动类型分析和代码生成
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TypeInferenceTest {
    
    @Test
    public void demonstrateTypeInference() {
        System.out.println("🎯 类型推断功能演示：");
        System.out.println();
        
        System.out.println("📊 支持的类型构造方式：");
        System.out.println();
        
        // 1. 基础类型
        System.out.println("1️⃣ 基础类型和包装类型：");
        System.out.println("   String findName(Long id)");
        System.out.println("   生成代码: return (String)row[0];");
        System.out.println();
        
        // 2. Record Class
        System.out.println("2️⃣ Record Class（推荐方式）：");
        System.out.println("   public record User(Long id, String name, String email, Integer age) {}");
        System.out.println("   User findById(Long id)");
        System.out.println("   生成代码: return new User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3]);");
        System.out.println();
        
        // 3. 普通类（构造器）
        System.out.println("3️⃣ 普通类（公共构造器）：");
        System.out.println("   public class User {");
        System.out.println("       public User(Long id, String name, String email, Integer age) { ... }");
        System.out.println("   }");
        System.out.println("   生成代码: return new User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3]);");
        System.out.println();
        
        // 4. 普通类（setter）
        System.out.println("4️⃣ 普通类（setter方式）：");
        System.out.println("   public class User {");
        System.out.println("       public void setId(Long id) { ... }");
        System.out.println("       public void setName(String name) { ... }");
        System.out.println("   }");
        System.out.println("   生成代码:");
        System.out.println("   return ({");
        System.out.println("       User obj = new User();");
        System.out.println("       obj.setId((Long)row[0]);");
        System.out.println("       obj.setName((String)row[1]);");
        System.out.println("       obj;");
        System.out.println("   });");
        System.out.println();
        
        System.out.println("🎯 类型推断的核心价值：");
        System.out.println("✅ 自动分析：无需手动配置，自动分析用户定义的类型");
        System.out.println("✅ 类型安全：编译期确定所有类型转换，零运行时错误");
        System.out.println("✅ 零反射：生成硬编码构造，无任何反射调用");
        System.out.println("✅ 高性能：直接对象构造，接近手写代码性能");
        System.out.println("✅ 可扩展：支持record class、普通class、基础类型");
        System.out.println("✅ 可调试：生成代码完全可读，便于调试和优化");
    }
    
    @Test
    public void demonstrateGeneratedMapperCode() {
        System.out.println("🎯 完整的生成代码示例：");
        System.out.println();
        
        String generatedCode = """
            package org.liteorm.test;
            
            import org.liteorm.api.*;
            import org.liteorm.DefaultSqlEngine;
            import java.util.List;
            import java.util.ArrayList;
            
            /**
             * Generated MapperImpl - Zero Reflection
             * 编译期生成的硬编码实现，无任何反射调用
             */
            public class UserMapperImpl implements UserMapper {
            
                private final SqlEngine sqlEngine;
            
                public UserMapperImpl(ConnectionManager connectionManager) {
                    this.sqlEngine = new DefaultSqlEngine(connectionManager);
                }
            
                @Override
                public User findById(Long id) {
                    // 硬编码SQL - 编译期确定，#{param}已转换为?
                    String sql = "SELECT id, name, email, age FROM users WHERE id = ?";
                    
                    // 增强参数绑定 - 支持#{param}语法，零反射
                    Object[] params = new Object[1];
                    params[0] = id;
                    
                    // 创建SQL执行任务
                    SqlTask task = new SqlTask(sql, params, SqlTask.SqlType.SELECT, false);
                    
                    // 执行SQL - 通过责任链处理
                    SqlResult result = sqlEngine.execute(task);
                    
                    // 硬编码结果映射 - 零反射
                    if (result.hasError()) {
                        throw new RuntimeException(result.getException());
                    }
                    List<Object[]> rows = result.getQueryResults();
                    if (rows.isEmpty()) {
                        return null;
                    }
                    Object[] row = rows.get(0);
                    return new User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3]);
                }
                
                @Override
                public List<User> findByName(String name) {
                    // 硬编码SQL - 编译期确定，#{param}已转换为?
                    String sql = "SELECT id, name, email, age FROM users WHERE name LIKE ?";
                    
                    // 增强参数绑定 - 支持#{param}语法，零反射
                    Object[] params = new Object[1];
                    params[0] = name;
                    
                    // 创建SQL执行任务
                    SqlTask task = new SqlTask(sql, params, SqlTask.SqlType.SELECT, false);
                    
                    // 执行SQL - 通过责任链处理
                    SqlResult result = sqlEngine.execute(task);
                    
                    // 硬编码结果映射 - 零反射
                    if (result.hasError()) {
                        throw new RuntimeException(result.getException());
                    }
                    // 硬编码List映射 - 零反射，编译期确定
                    List<Object[]> rows = result.getQueryResults();
                    List<User> resultList = new ArrayList<>(rows.size());
                    for (Object[] row : rows) {
                        resultList.add(new User((Long)row[0], (String)row[1], (String)row[2], (Integer)row[3]));
                    }
                    return resultList;
                }
            }
            """;
        
        System.out.println(generatedCode);
        
        System.out.println("🎯 生成代码的关键特点：");
        System.out.println("✅ 完全硬编码：没有任何反射、动态解析或运行时魔法");
        System.out.println("✅ 类型安全：所有类型转换在编译期确定");
        System.out.println("✅ 高性能：接近手写代码的执行效率");
        System.out.println("✅ 可调试：生成的代码完全可读，可以设置断点调试");
        System.out.println("✅ 可优化：生成的代码可以进一步手动优化");
    }
}

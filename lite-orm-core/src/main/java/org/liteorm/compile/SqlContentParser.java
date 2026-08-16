package org.liteorm.compile;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

/**
 * SQL内容解析器接口 - 解析XML/注解中的动态SQL为AST
 * 
 * 职责：
 * 1. 解析XML文件中的SQL内容
 * 2. 解析注解中的动态SQL字符串
 * 3. 输出标准化的AST节点树
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
interface SqlContentParser {
    
    /**
     * 解析方法的SQL内容
     * 
     * @param method 方法元素
     * @return SQL解析结果，如果不支持则返回null
     */
    SqlParseResult parseSql(ExecutableElement method);
    
    /**
     * 检查是否支持解析该方法
     * 
     * @param method 方法元素
     * @return 是否支持
     */
    boolean supports(ExecutableElement method);
    
    /**
     * 获取解析器名称
     * 
     * @return 解析器名称
     */
    String getParserName();
    
    /**
     * SQL解析结果
     */
    record SqlParseResult(
        String sqlTemplate,           // SQL模板（可能包含#{param}或动态标签）
        SqlType sqlType,             // SQL类型
        SqlSourceType sourceType,    // SQL来源类型
        boolean isDynamic,           // 是否为动态SQL
        List<ParameterInfo> parameters, // 参数信息
        AstNode astNode             // 动态SQL的AST节点（如果有）
    ) {}
    
    /**
     * 参数信息
     */
    record ParameterInfo(
        String name,        // 参数名
        String accessCode,  // 访问代码（如user.name()）
        String typeName     // 类型名称
    ) {}
    
    /**
     * SQL来源类型
     */
    enum SqlSourceType {
        XML,           // XML文件中的SQL
        ANNOTATION,    // 注解中的SQL
        SCRIPT         // 注解中的<script>标签
    }
    
    /**
     * SQL类型
     */
    enum SqlType {
        SELECT, INSERT, UPDATE, DELETE, BATCH
    }
}

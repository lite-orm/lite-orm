package org.liteorm.compile;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

/**
 * 动态SQL解析器接口 - 基于第一性原理的SQL解析抽象
 * 
 * 物理职责：
 * 1. 解析各种输入源中的SQL内容（物理必需：理解输入格式）
 * 2. 构建动态SQL的AST结构（物理必需：结构化表示）
 * 3. 分析参数依赖关系（物理必需：参数绑定基础）
 * 4. 输出标准化的SQL信息（物理必需：统一后续处理）
 * 
 * 设计原则：
 * - 解析和渲染分离：只负责解析，不负责代码生成
 * - 输入源无关：支持注解、XML、Provider等多种输入
 * - 结构化输出：使用AST节点而非字符串
 * 
 * 支持的输入源：
 * - 注解中的SQL字符串
 * - 注解中的<script>XML内容  
 * - 外部XML文件中的SQL
 * - @SelectProvider等动态Provider
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface DynamicSqlParser {
    
    /**
     * 解析方法的SQL内容
     * 
     * @param method 方法元素（包含注解、参数等所有信息）
     * @return SQL解析结果，如果不支持则返回null
     */
    SqlInfo parseSql(ExecutableElement method);
    
    /**
     * 检查是否支持解析该方法
     * 
     * @param method 方法元素
     * @return 是否支持
     */
    boolean supports(ExecutableElement method);
    
    /**
     * 获取解析器名称（用于调试）
     * 
     * @return 解析器名称
     */
    String getParserName();
    
    /**
     * SQL信息封装
     */
    class SqlInfo {
        private final String sqlTemplate;                    // SQL模板（可能包含#{param}或动态标签）
        private final SqlType sqlType;                      // SQL类型
        private final SqlSourceType sourceType;             // SQL来源类型
        private final boolean isDynamic;                    // 是否为动态SQL
        private final List<ParameterInfo> parameters;       // 参数信息
        private final DynamicNode dynamicNode;              // 动态SQL的AST节点（如果有）
        
        public SqlInfo(String sqlTemplate, SqlType sqlType, SqlSourceType sourceType,
                      boolean isDynamic, List<ParameterInfo> parameters, DynamicNode dynamicNode) {
            this.sqlTemplate = sqlTemplate;
            this.sqlType = sqlType;
            this.sourceType = sourceType;
            this.isDynamic = isDynamic;
            this.parameters = parameters;
            this.dynamicNode = dynamicNode;
        }
        
        // Getters
        public String getSqlTemplate() { return sqlTemplate; }
        public SqlType getSqlType() { return sqlType; }
        public SqlSourceType getSourceType() { return sourceType; }
        public boolean isDynamic() { return isDynamic; }
        public List<ParameterInfo> getParameters() { return parameters; }
        public DynamicNode getDynamicNode() { return dynamicNode; }
    }
    
    /**
     * 参数信息
     */
    class ParameterInfo {
        private final String name;           // 参数名
        private final String accessCode;    // 访问代码（如user.name()）
        private final String typeName;      // 类型名称
        
        public ParameterInfo(String name, String accessCode, String typeName) {
            this.name = name;
            this.accessCode = accessCode;
            this.typeName = typeName;
        }
        
        public String getName() { return name; }
        public String getAccessCode() { return accessCode; }
        public String getTypeName() { return typeName; }
    }
    
    /**
     * SQL来源类型
     */
    enum SqlSourceType {
        ANNOTATION,     // 注解中的SQL
        XML,           // XML文件中的SQL
        PROVIDER,      // @SelectProvider等动态Provider
        SCRIPT         // 注解中的<script>标签
    }
    
    /**
     * SQL类型
     */
    enum SqlType {
        SELECT, INSERT, UPDATE, DELETE, BATCH
    }
    
    /**
     * 动态SQL节点接口 - AST结构
     */
    interface DynamicNode {
        /**
         * 节点类型
         */
        NodeType getNodeType();
        
        /**
         * 子节点
         */
        List<DynamicNode> getChildren();
        
        /**
         * 节点类型枚举
         */
        enum NodeType {
            TEXT,       // 文本节点
            IF,         // <if>条件节点
            FOREACH,    // <foreach>循环节点
            CHOOSE,     // <choose>选择节点
            WHEN,       // <when>条件分支
            OTHERWISE,  // <otherwise>默认分支
            WHERE,      // <where>条件包装
            SET,        // <set>更新包装
            TRIM        // <trim>修剪包装
        }
    }
    
    /**
     * 文本节点 - 静态SQL片段
     */
    class TextNode implements DynamicNode {
        private final String text;
        
        public TextNode(String text) {
            this.text = text;
        }
        
        @Override
        public NodeType getNodeType() { return NodeType.TEXT; }
        
        @Override
        public List<DynamicNode> getChildren() { return List.of(); }
        
        public String getText() { return text; }
    }
    
    /**
     * IF条件节点
     */
    class IfNode implements DynamicNode {
        private final String test;
        private final List<DynamicNode> children;
        
        public IfNode(String test, List<DynamicNode> children) {
            this.test = test;
            this.children = children;
        }
        
        @Override
        public NodeType getNodeType() { return NodeType.IF; }
        
        @Override
        public List<DynamicNode> getChildren() { return children; }
        
        public String getTest() { return test; }
    }
    
    /**
     * FOREACH循环节点
     */
    class ForeachNode implements DynamicNode {
        private final String collection;
        private final String item;
        private final String separator;
        private final String open;
        private final String close;
        private final List<DynamicNode> children;
        
        public ForeachNode(String collection, String item, String separator, 
                          String open, String close, List<DynamicNode> children) {
            this.collection = collection;
            this.item = item;
            this.separator = separator;
            this.open = open;
            this.close = close;
            this.children = children;
        }
        
        @Override
        public NodeType getNodeType() { return NodeType.FOREACH; }
        
        @Override
        public List<DynamicNode> getChildren() { return children; }
        
        public String getCollection() { return collection; }
        public String getItem() { return item; }
        public String getSeparator() { return separator; }
        public String getOpen() { return open; }
        public String getClose() { return close; }
    }
}

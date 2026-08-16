package org.liteorm.compile;

import java.util.List;

/**
 * AST节点接口 - 动态SQL的抽象语法树
 * 
 * 职责：
 * 1. 表示动态SQL的树形结构
 * 2. 支持各种动态标签（if、foreach、choose等）
 * 3. 为代码生成提供结构化数据
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
interface AstNode {
    
    /**
     * 节点类型
     */
    NodeType getNodeType();
    
    /**
     * 子节点
     */
    List<AstNode> getChildren();
    
    /**
     * 节点类型枚举
     */
    enum NodeType {
        CONTAINER,  // 容器节点
        TEXT,       // 文本节点
        IF,         // <if>条件节点
        FOREACH,    // <foreach>循环节点
        CHOOSE,     // <choose>选择节点
        WHEN,       // <when>条件分支
        OTHERWISE,  // <otherwise>默认分支
        WHERE,      // <where>条件包装
        SET,        // <set>更新包装
        TRIM,       // <trim>修剪包装
        BIND,       // <bind>局部变量绑定
        INCLUDE,    // <include> SQL片段引用
    }

    /**
     * 容器节点 - 保留多个混合子节点的顺序。
     */
    record ContainerNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.CONTAINER; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * 文本节点 - 静态SQL片段
     */
    record TextNode(String text) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.TEXT; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
    
    /**
     * IF条件节点
     */
    record IfNode(String test, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.IF; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * FOREACH循环节点
     */
    record ForeachNode(String collection, String item, String separator, 
                       String open, String close, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.FOREACH; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * CHOOSE选择节点
     */
    record ChooseNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.CHOOSE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * WHEN条件分支节点
     */
    record WhenNode(String test, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.WHEN; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * OTHERWISE默认分支节点
     */
    record OtherwiseNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.OTHERWISE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * WHERE条件包装节点
     */
    record WhereNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.WHERE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * SET更新包装节点
     */
    record SetNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.SET; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * TRIM修剪包装节点
     */
    record TrimNode(String prefix, String suffix, String prefixOverrides, 
                   String suffixOverrides, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.TRIM; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * BIND局部变量绑定节点
     */
    record BindNode(String name, String value) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.BIND; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
    
    /**
     * INCLUDE SQL片段引用节点
     */
    record IncludeNode(String refId) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.INCLUDE; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
}

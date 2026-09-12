package org.liteorm.compile;

import java.util.List;

/**
 * Dynamic SQL abstract syntax tree node.
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
interface AstNode {
    
    /**
     * Returns the node type.
     */
    NodeType getNodeType();
    
    /**
     * Returns child nodes in source order.
     */
    List<AstNode> getChildren();
    
    /**
     * Supported dynamic SQL node types.
     */
    enum NodeType {
        CONTAINER,
        TEXT,
        IF,
        FOREACH,
        CHOOSE,
        WHEN,
        OTHERWISE,
        WHERE,
        SET,
        TRIM,
        BIND,
        INCLUDE,
    }

    /**
     * Container that preserves mixed child-node order.
     */
    record ContainerNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.CONTAINER; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Static SQL text fragment.
     */
    record TextNode(String text) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.TEXT; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
    
    /**
     * Conditional {@code if} node.
     */
    record IfNode(String test, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.IF; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Iteration node.
     */
    record ForeachNode(String collection, String item, String separator, 
                       String open, String close, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.FOREACH; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Choice container node.
     */
    record ChooseNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.CHOOSE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Conditional choice branch.
     */
    record WhenNode(String test, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.WHEN; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Default choice branch.
     */
    record OtherwiseNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.OTHERWISE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Conditional WHERE wrapper.
     */
    record WhereNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.WHERE; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Conditional SET wrapper.
     */
    record SetNode(List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.SET; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Configurable trim wrapper.
     */
    record TrimNode(String prefix, String suffix, String prefixOverrides, 
                   String suffixOverrides, List<AstNode> children) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.TRIM; }
        @Override
        public List<AstNode> getChildren() { return children; }
    }
    
    /**
     * Local expression binding node.
     */
    record BindNode(String name, String value) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.BIND; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
    
    /**
     * SQL fragment reference node.
     */
    record IncludeNode(String refId) implements AstNode {
        @Override
        public NodeType getNodeType() { return NodeType.INCLUDE; }
        @Override
        public List<AstNode> getChildren() { return List.of(); }
    }
}

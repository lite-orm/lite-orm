package org.lite.parser.xml.node;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/11/13 15:19
 */
@Slf4j
@Getter
@Setter
public abstract class FragmentNode {
    private String nodeType;
    private List<FragmentNode> children = new ArrayList<>();

    private static final String dynamicPrefix = "#{";
    private static final String staticPrefix = "${";
    private static final String suffix = "}";

    /**
     * build tree
     * @param node
     */
    public void build(Node node) {
        if (node == null) {
            return;
        }
        // 设置当前节点的类型
        if (node instanceof Element element) {
            this.nodeType = element.getName();
        }
        List<Node> childNodes = node.selectNodes("*");
        for (Node childNode : childNodes) {
            FragmentNode childFragment = NodeFactory.createFragmentNode(childNode);
            if (childFragment != null) {
                // 递归初始化子节点
                childFragment.build(childNode);
                this.children.add(childFragment);
            }
        }
    }

    protected String parseChild() {
        StringBuilder builder = new StringBuilder();
        for (FragmentNode child : getChildren()) {
            builder.append(child.parse()).append("\n");
        }
        return builder.toString();
    }

    /**
     * @return
     */
    public abstract String parse();

}

package org.lite.parser.xml.node;

import org.dom4j.Element;

/**
 * @author qingbozhang
 * @since 2024/11/14 14:08
 */
public class IfFragmentNode extends FragmentNode {
    private Element element;
    private String test;

    public IfFragmentNode(Element element) {
        this.element = element;
        this.test = element.attributeValue("test");
    }

    @Override
    public String parse() {
        return "if (" + test + ") {\n" + parseChild() + "\n}";
    }
}

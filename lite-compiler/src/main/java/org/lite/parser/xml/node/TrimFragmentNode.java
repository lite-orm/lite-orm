package org.lite.parser.xml.node;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;

/**
 * @author qingbozhang
 * @since 2024/11/13 15:47
 */
@Slf4j
public class TrimFragmentNode extends FragmentNode {
    private Element element;

    public TrimFragmentNode(Element element) {
        this.element = element;
    }

    @Override
    public String parse() {
        String result = parseChild();
        result += "builder = new StringBuilder(builder.toString().trim());";
        return result;
    }

}

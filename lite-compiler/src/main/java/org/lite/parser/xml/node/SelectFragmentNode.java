package org.lite.parser.xml.node;

import lombok.Getter;
import lombok.Setter;
import org.dom4j.Element;

import java.lang.reflect.Method;

/**
 * @author qingbozhang
 * @since 2024/11/13 19:13
 */
@Getter
@Setter
public class SelectFragmentNode extends FragmentNode {
    private String resultType;
    private String parameterType;
//    private Method method;

    public SelectFragmentNode(Element element) {
        resultType = element.attributeValue("resultType");
        parameterType = element.attributeValue("parameterType");
    }

    @Override
    public String parse() {
        return parseChild();
    }
}

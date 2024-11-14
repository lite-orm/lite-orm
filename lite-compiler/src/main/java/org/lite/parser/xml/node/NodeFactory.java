package org.lite.parser.xml.node;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;

/**
 * @author qingbozhang
 * @since 2024/11/14 16:56
 */
@Slf4j
public class NodeFactory {

    public static FragmentNode createFragmentNode(Node node) {
        return switch (node) {
            case Element element -> handleElement(element);
            case Text text -> new TextFragmentNode(text);
            case null, default -> {
                assert node != null;
                log.info("ignore {}", node.getClass().getSimpleName());
                yield null;
            }
        };
    }

    private static FragmentNode handleElement(Element el) {
        String name = el.getName().toLowerCase();
        return switch (name) {
            case "if" -> new IfFragmentNode(el);
            case "trim" -> new TrimFragmentNode(el);
            default -> null;
        };
    }
}

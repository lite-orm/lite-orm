package org.lite.parser.xml;

import lombok.Data;
import org.dom4j.Element;
import org.lite.parser.xml.node.FragmentNode;

import java.util.List;

/**
 * A xml file
 * @author qingbozhang
 * @since 2024/11/13 15:21
 */
@Data
public class FragmentRoot {
    protected String id;
    protected String namespace;
    private List<FragmentNode> fragmentNodes;

    public FragmentRoot(Element element) {
        // TODO
    }

    public byte[] getParsedSql() {
        // TODO
        return null;
    }
}

package org.lite.parser.xml.node;

import lombok.Getter;
import org.dom4j.Text;

/**
 * @author qingbozhang
 * @since 2024/11/14 14:50
 */

@Getter
public class TextFragmentNode extends FragmentNode {
    private String text;

    public TextFragmentNode(Text text) {
        this.text = text.getText().trim();
    }

    @Override
    public String parse() {
        return "builder.append(" + text + ");";
    }
}

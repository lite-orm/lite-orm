package org.lite.parser.xml;

import lombok.Data;

import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/11/13 19:39
 */
@Data
public class ParseResult {
    private List<String> fragment;
    private List<Expression> expressions;

    @Data
    public static class Expression {
        private String text;
        private boolean dynamic;
    }
}

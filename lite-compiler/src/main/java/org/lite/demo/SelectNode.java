package org.lite.demo;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * @author qingbozhang
 * @since 创建于 2024/11/12 11:24
 */
public class SelectNode {
    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlText
    private String body;
}

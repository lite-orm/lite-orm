package org.lite.demo;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * TODO 单例模式注册
 *
 * @author qingbozhang
 * @since 2024/11/12 11:20
 */
@Slf4j
public class XmlParser {

//    private static XmlMapper xmlMapper = new XmlMapper();

    /**
     * 返回占位符
     */
    public static String render(InputStream stream, String id, Map<String, Object> params) throws IOException {
//        JsonNode jsonNode = getById(stream, id);
//        // TODO 解析子标签
//        String body = jsonNode.get("").textValue().trim();
//        return doRender(body, params);
        return "";
    }

//    private static JsonNode getById(InputStream stream, String id) throws IOException {
//        JsonNode rootNode = xmlMapper.readTree(stream);
//        Iterator<JsonNode> node = rootNode.elements();
//        JsonNode targetNode = null;
//
//        while (node.hasNext()) {
//            JsonNode selectNode = node.next();
//            // 检查 id 属性
//            if (selectNode.has("id") && id.equals(selectNode.get("id").asText())) {
//                targetNode = selectNode;
//                break;
//            }
//        }
//        return targetNode;
//    }

//    private static String doRender(String content, Map<String, Object> params) {
//        if (params == null || params.isEmpty()) {
//            return content;
//        }
//        if (Objects.isNull(content)) {
//            return content;
//        }
//        // TODO 先简单粗暴
//        for (Map.Entry<String, Object> entry : params.entrySet()) {
//            String key = "#{" + entry.getKey() + "}";
//            content = content.replace(key, "?");
//        }
//        return content;
//    }
}

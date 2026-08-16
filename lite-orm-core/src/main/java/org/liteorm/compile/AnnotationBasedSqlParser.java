package org.liteorm.compile;

import org.liteorm.annotation.Delete;
import org.liteorm.annotation.Batch;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Select;
import org.liteorm.annotation.Update;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.lang.model.element.ExecutableElement;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于注解的SQL解析器实现
 * 
 * 职责：
 * 1. 解析注解中的SQL内容
 * 2. 支持<script>标签的动态SQL
 * 3. 构建AST节点树
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
final class AnnotationBasedSqlParser implements SqlContentParser {
    
    @Override
    public SqlParseResult parseSql(ExecutableElement method) {
        // 1. 检查注解
        String sqlContent = getSqlFromAnnotation(method);
        if (sqlContent == null) {
            return null;
        }

        // 2. 判断是否为动态SQL
        boolean isDynamic = containsDynamicTags(sqlContent);

        // 3. 解析参数
        List<ParameterInfo> parameters = parseParameters(method);

        // 4. 解析AST节点
        AstNode astNode = isDynamic ? parseAstNode(sqlContent) : null;

        // 5. 确定SQL类型
        SqlType sqlType = determineSqlType(method);

        return new SqlParseResult(
            sqlContent,
            sqlType,
            SqlSourceType.ANNOTATION,
            isDynamic,
            parameters,
            astNode
        );
    }
    
    @Override
    public boolean supports(ExecutableElement method) {
        return getSqlFromAnnotation(method) != null;
    }
    
    @Override
    public String getParserName() {
        return "AnnotationBasedSqlParser";
    }
    
    /**
     * 从注解获取SQL内容
     */
    private String getSqlFromAnnotation(ExecutableElement method) {
        // 检查@Select注解
        Select selectAnnotation = method.getAnnotation(Select.class);
        if (selectAnnotation != null) {
            return String.join("\n", selectAnnotation.value());
        }
        
        // 检查@Insert注解
        Insert insertAnnotation = method.getAnnotation(Insert.class);
        if (insertAnnotation != null) {
            return String.join("\n", insertAnnotation.value());
        }
        
        // 检查@Update注解
        Update updateAnnotation = method.getAnnotation(Update.class);
        if (updateAnnotation != null) {
            return String.join("\n", updateAnnotation.value());
        }
        
        // 检查@Delete注解
        Delete deleteAnnotation = method.getAnnotation(Delete.class);
        if (deleteAnnotation != null) {
            return String.join("\n", deleteAnnotation.value());
        }

        Batch batchAnnotation = method.getAnnotation(Batch.class);
        if (batchAnnotation != null) {
            return String.join("\n", batchAnnotation.value());
        }
        
        return null;
    }
    
    /**
     * 检查是否包含动态标签
     */
    private boolean containsDynamicTags(String sqlContent) {
        return sqlContent.contains("<if") ||
               sqlContent.contains("<foreach") ||
               sqlContent.contains("<choose") ||
               sqlContent.contains("<where") ||
               sqlContent.contains("<set") ||
               sqlContent.contains("<trim") ||
               sqlContent.contains("<bind") ||
               sqlContent.contains("<include") ||
               sqlContent.contains("<script");
    }
    
    /**
     * 解析AST节点
     */
    private AstNode parseAstNode(String sqlContent) {
        try {
            // 检查是否有<script>标签
            if (sqlContent.contains("<script>")) {
                String scriptContent = extractScriptContent(sqlContent);
                return parseXmlContent(scriptContent);
            } else {
                // 直接解析内容
                return parseXmlContent(sqlContent);
            }
        } catch (Exception e) {
            System.err.println("AST解析失败: " + e.getMessage());
            return new AstNode.TextNode(sqlContent);
        }
    }
    
    /**
     * 提取script标签内容
     */
    private String extractScriptContent(String sqlContent) {
        int start = sqlContent.indexOf("<script>") + 8;
        int end = sqlContent.lastIndexOf("</script>");
        if (start > 7 && end > start) {
            return sqlContent.substring(start, end);
        }
        return sqlContent;
    }
    
    /**
     * 解析XML内容
     */
    private AstNode parseXmlContent(String xmlContent) throws Exception {
        // 包装为完整的XML文档
        String fullXml = "<root>" + xmlContent + "</root>";
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(fullXml.getBytes()));
        
        Element root = document.getDocumentElement();
        return new AstNode.ContainerNode(parseChildren(root));
    }
    
    /**
     * 解析XML元素
     */
    private AstNode parseElement(Element element) {
        String tagName = element.getTagName();
        
        switch (tagName) {
            case "root":
                return new AstNode.ContainerNode(parseChildren(element));
            case "if":
                return parseIfElement(element);
            case "foreach":
                return parseForeachElement(element);
            case "choose":
                return parseChooseElement(element);
            case "when":
                return parseWhenElement(element);
            case "otherwise":
                return parseOtherwiseElement(element);
            case "where":
                return parseWhereElement(element);
            case "set":
                return parseSetElement(element);
            case "trim":
                return parseTrimElement(element);
            case "bind":
                return parseBindElement(element);
            case "include":
                return parseIncludeElement(element);
            default:
                List<AstNode> children = parseChildren(element);
                return children.isEmpty()
                    ? new AstNode.TextNode(getTextContent(element))
                    : new AstNode.ContainerNode(children);
        }
    }
    
    /**
     * 解析IF元素
     */
    private AstNode parseIfElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildren(element);
        return new AstNode.IfNode(test, children);
    }
    
    /**
     * 解析FOREACH元素
     */
    private AstNode parseForeachElement(Element element) {
        String collection = element.getAttribute("collection");
        String item = element.getAttribute("item");
        String separator = element.getAttribute("separator");
        String open = element.getAttribute("open");
        String close = element.getAttribute("close");
        List<AstNode> children = parseChildren(element);
        return new AstNode.ForeachNode(collection, item, separator, open, close, children);
    }
    
    /**
     * 解析CHOOSE元素
     */
    private AstNode parseChooseElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.ChooseNode(children);
    }
    
    /**
     * 解析WHEN元素
     */
    private AstNode parseWhenElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildren(element);
        return new AstNode.WhenNode(test, children);
    }
    
    /**
     * 解析OTHERWISE元素
     */
    private AstNode parseOtherwiseElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.OtherwiseNode(children);
    }
    
    /**
     * 解析WHERE元素
     */
    private AstNode parseWhereElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.WhereNode(children);
    }
    
    /**
     * 解析SET元素
     */
    private AstNode parseSetElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.SetNode(children);
    }
    
    /**
     * 解析TRIM元素
     */
    private AstNode parseTrimElement(Element element) {
        String prefix = element.getAttribute("prefix");
        String suffix = element.getAttribute("suffix");
        String prefixOverrides = element.getAttribute("prefixOverrides");
        String suffixOverrides = element.getAttribute("suffixOverrides");
        List<AstNode> children = parseChildren(element);
        return new AstNode.TrimNode(prefix, suffix, prefixOverrides, suffixOverrides, children);
    }
    
    /**
     * 解析BIND元素
     */
    private AstNode parseBindElement(Element element) {
        String name = element.getAttribute("name");
        String value = element.getAttribute("value");
        return new AstNode.BindNode(name, value);
    }
    
    /**
     * 解析INCLUDE元素
     */
    private AstNode parseIncludeElement(Element element) {
        String refId = element.getAttribute("refid");
        return new AstNode.IncludeNode(refId);
    }
    
    /**
     * 解析子元素
     */
    private List<AstNode> parseChildren(Element element) {
        List<AstNode> children = new ArrayList<>();
        NodeList nodeList = element.getChildNodes();
        
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent().trim();
                if (!text.isEmpty()) {
                    children.add(new AstNode.TextNode(text));
                }
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add(parseElement((Element) node));
            }
        }
        
        return children;
    }
    
    /**
     * 获取文本内容
     */
    private String getTextContent(Element element) {
        StringBuilder text = new StringBuilder();
        NodeList nodeList = element.getChildNodes();
        
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                text.append(node.getTextContent());
            }
        }
        
        return text.toString().trim();
    }
    
    /**
     * 解析参数
     */
    private List<ParameterInfo> parseParameters(ExecutableElement method) {
        List<ParameterInfo> parameters = new ArrayList<>();
        var methodParams = method.getParameters();
        
        for (var param : methodParams) {
            String paramName = param.getSimpleName().toString();
            String paramType = param.asType().toString();
            parameters.add(new ParameterInfo(paramName, paramName, paramType));
        }
        
        return parameters;
    }
    
    /**
     * 确定SQL类型
     */
    private SqlType determineSqlType(ExecutableElement method) {
        if (method.getAnnotation(Select.class) != null) {
            return SqlType.SELECT;
        } else if (method.getAnnotation(Insert.class) != null) {
            return SqlType.INSERT;
        } else if (method.getAnnotation(Update.class) != null) {
            return SqlType.UPDATE;
        } else if (method.getAnnotation(Delete.class) != null) {
            return SqlType.DELETE;
        } else if (method.getAnnotation(Batch.class) != null) {
            return SqlType.BATCH;
        } else {
            return SqlType.SELECT; // 默认
        }
    }
}

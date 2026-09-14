package io.github.kervix.compile;

import io.github.kervix.annotation.Delete;
import io.github.kervix.annotation.Batch;
import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Select;
import io.github.kervix.annotation.Update;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses SQL and dynamic scripts declared through Mapper annotations.
 * 
 * @author kervix
 * @since 2024/10/01
 */
final class AnnotationBasedSqlParser implements SqlContentParser {
    
    @Override
    public SqlParseResult parseSql(ExecutableElement method) {
        String sqlContent = getSqlFromAnnotation(method);
        if (sqlContent == null) {
            return null;
        }

        boolean isDynamic = containsDynamicTags(sqlContent);

        List<ParameterInfo> parameters = parseParameters(method);

        AstNode astNode = isDynamic ? parseAstNode(sqlContent) : null;

        SqlType sqlType = determineSqlType(method);

        return new SqlParseResult(
            sqlContent,
            sqlType,
            SqlSourceType.ANNOTATION,
            isDynamic,
            parameters,
            astNode,
            null
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
     * Returns SQL declared by a supported statement annotation.
     */
    private String getSqlFromAnnotation(ExecutableElement method) {
        Select selectAnnotation = method.getAnnotation(Select.class);
        if (selectAnnotation != null) {
            return String.join("\n", selectAnnotation.value());
        }
        
        Insert insertAnnotation = method.getAnnotation(Insert.class);
        if (insertAnnotation != null) {
            return String.join("\n", insertAnnotation.value());
        }
        
        Update updateAnnotation = method.getAnnotation(Update.class);
        if (updateAnnotation != null) {
            return String.join("\n", updateAnnotation.value());
        }
        
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
     * Returns whether the SQL contains supported dynamic tags.
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
     * Parses the dynamic SQL AST.
     */
    private AstNode parseAstNode(String sqlContent) {
        if (sqlContent.contains("<script>")) {
            String scriptContent = extractScriptContent(sqlContent);
            return parseXmlContent(scriptContent);
        }
        return parseXmlContent(sqlContent);
    }
    
    /**
     * Extracts the contents of a {@code script} element.
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
     * Parses dynamic SQL XML content.
     */
    private AstNode parseXmlContent(String xmlContent) {
        String fullXml = "<root>" + xmlContent + "</root>";
        Document document = SecureXml.parse(fullXml, "annotation SQL script");
        
        Element root = document.getDocumentElement();
        return new AstNode.ContainerNode(parseChildren(root));
    }
    
    /**
     * Parses one XML element.
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
                throw new IllegalArgumentException(
                    "Unsupported annotation SQL tag <" + tagName + ">");
        }
    }
    
    /**
     * Parses an {@code if} element.
     */
    private AstNode parseIfElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildren(element);
        return new AstNode.IfNode(test, children);
    }
    
    /**
     * Parses a {@code foreach} element.
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
     * Parses a {@code choose} element.
     */
    private AstNode parseChooseElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.ChooseNode(children);
    }
    
    /**
     * Parses a {@code when} element.
     */
    private AstNode parseWhenElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildren(element);
        return new AstNode.WhenNode(test, children);
    }
    
    /**
     * Parses an {@code otherwise} element.
     */
    private AstNode parseOtherwiseElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.OtherwiseNode(children);
    }
    
    /**
     * Parses a {@code where} element.
     */
    private AstNode parseWhereElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.WhereNode(children);
    }
    
    /**
     * Parses a {@code set} element.
     */
    private AstNode parseSetElement(Element element) {
        List<AstNode> children = parseChildren(element);
        return new AstNode.SetNode(children);
    }
    
    /**
     * Parses a {@code trim} element.
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
     * Parses a {@code bind} element.
     */
    private AstNode parseBindElement(Element element) {
        String name = element.getAttribute("name");
        String value = element.getAttribute("value");
        return new AstNode.BindNode(name, value);
    }
    
    /**
     * Parses an {@code include} element.
     */
    private AstNode parseIncludeElement(Element element) {
        String refId = element.getAttribute("refid");
        return new AstNode.IncludeNode(refId);
    }
    
    /**
     * Parses child nodes in source order.
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
     * Returns direct text content.
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
     * Describes Mapper method parameters.
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
     * Determines the statement type.
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
            return SqlType.SELECT;
        }
    }
}

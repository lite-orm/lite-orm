package org.liteorm.compile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于XML的SQL解析器实现
 * 
 * 职责：
 * 1. 解析XML文件中的SQL内容
 * 2. 构建AST节点树
 * 3. 支持MyBatis XML格式
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
public class XmlBasedSqlParser implements SqlContentParser {
    
    private static final Map<String, Document> xmlCache = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Element>> sqlFragmentCache = new ConcurrentHashMap<>();
    
    // 线程本地变量，存储当前解析的XML路径（用于include片段引用）
    private static final ThreadLocal<String> currentXmlPath = new ThreadLocal<>();
    
    @Override
    public SqlParseResult parseSql(ExecutableElement method) {
        try {
            // 1. 获取XML文件路径
            String xmlPath = getXmlPath(method);
            if (xmlPath == null) {
                return null;
            }

            // 设置当前XML路径（用于include引用）
            currentXmlPath.set(xmlPath);

            // 2. 解析XML文件
            Document document = getXmlDocument(xmlPath);
            if (document == null) {
                return null;
            }

            // 3. 查找对应的SQL语句
            String methodName = method.getSimpleName().toString();
            Element sqlElement = findSqlElement(document, methodName);
            if (sqlElement == null) {
                return null;
            }

            // 4. 解析SQL内容
            return parseSqlElement(sqlElement, method);
        } finally {
            currentXmlPath.remove();
        }
    }
    
    @Override
    public boolean supports(ExecutableElement method) {
        // 检查是否有对应的XML文件
        return getXmlPath(method) != null;
    }

    public boolean hasMapperResource(ExecutableElement method) {
        String xmlPath = getXmlPath(method);
        return xmlPath != null && getXmlDocument(xmlPath) != null;
    }
    
    @Override
    public String getParserName() {
        return "XmlBasedSqlParser";
    }
    
    /**
     * 获取XML文件路径
     */
    private String getXmlPath(ExecutableElement method) {
        if (!(method.getEnclosingElement() instanceof TypeElement typeElement)) {
            return null;
        }

        String qualifiedName = typeElement.getQualifiedName().toString();
        int packageEnd = qualifiedName.lastIndexOf('.');
        String packagePath = packageEnd >= 0
            ? qualifiedName.substring(0, packageEnd).replace('.', '/')
            : "";
        String className = typeElement.getSimpleName().toString();

        return packagePath.isEmpty()
            ? "/" + className + ".xml"
            : "/" + packagePath + "/" + className + ".xml";
    }
    
    /**
     * 获取XML文档
     */
    private Document getXmlDocument(String xmlPath) {
        return xmlCache.computeIfAbsent(xmlPath, path -> {
            try (InputStream is = openXmlStream(path)) {
                if (is == null) {
                    return null;
                }
                
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
                Document doc = builder.parse(is);
                
                // 缓存SQL片段
                cacheSqlFragments(xmlPath, doc);
                
                return doc;
            } catch (ParserConfigurationException | SAXException | IOException e) {
                System.err.println("XML文件解析失败: " + path + ", " + e.getMessage());
                return null;
            }
        });
    }

    private InputStream openXmlStream(String xmlPath) throws IOException {
        InputStream classpathStream = getClass().getResourceAsStream(xmlPath);
        if (classpathStream != null) {
            return classpathStream;
        }

        String relativePath = xmlPath.startsWith("/") ? xmlPath.substring(1) : xmlPath;
        String userDir = System.getProperty("user.dir");
        List<Path> candidates = List.of(
            Path.of(userDir, "src", "main", "resources", relativePath),
            Path.of(userDir, "src", "test", "resources", relativePath),
            Path.of(userDir, "lite-orm-core", "src", "main", "resources", relativePath),
            Path.of(userDir, "lite-orm-core", "src", "test", "resources", relativePath)
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return Files.newInputStream(candidate);
            }
        }

        return null;
    }
    
    /**
     * 缓存SQL片段
     */
    private void cacheSqlFragments(String xmlPath, Document document) {
        Map<String, Element> fragments = new HashMap<>();
        NodeList sqlNodes = document.getElementsByTagName("sql");
        
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElement = (Element) sqlNodes.item(i);
            String id = sqlElement.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                fragments.put(id, sqlElement);
            }
        }
        
        if (!fragments.isEmpty()) {
            sqlFragmentCache.put(xmlPath, fragments);
        }
    }
    
    /**
     * 查找SQL元素
     */
    private Element findSqlElement(Document document, String methodName) {
        NodeList selectNodes = document.getElementsByTagName("select");
        for (int i = 0; i < selectNodes.getLength(); i++) {
            Element element = (Element) selectNodes.item(i);
            if (methodName.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        
        NodeList insertNodes = document.getElementsByTagName("insert");
        for (int i = 0; i < insertNodes.getLength(); i++) {
            Element element = (Element) insertNodes.item(i);
            if (methodName.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        
        NodeList updateNodes = document.getElementsByTagName("update");
        for (int i = 0; i < updateNodes.getLength(); i++) {
            Element element = (Element) updateNodes.item(i);
            if (methodName.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        
        NodeList deleteNodes = document.getElementsByTagName("delete");
        for (int i = 0; i < deleteNodes.getLength(); i++) {
            Element element = (Element) deleteNodes.item(i);
            if (methodName.equals(element.getAttribute("id"))) {
                return element;
            }
        }

        NodeList batchNodes = document.getElementsByTagName("batch");
        for (int i = 0; i < batchNodes.getLength(); i++) {
            Element element = (Element) batchNodes.item(i);
            if (methodName.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        
        return null;
    }
    
    /**
     * 解析SQL元素
     */
    private SqlParseResult parseSqlElement(Element sqlElement, ExecutableElement method) {
        validateResultMapping(sqlElement);
        validateSupportedTags(sqlElement);
        String sqlType = sqlElement.getTagName().toUpperCase();
        String sqlContent = getSqlContent(sqlElement);
        boolean isDynamic = containsDynamicTags(sqlElement);
        
        // 解析参数
        List<ParameterInfo> parameters = parseParameters(method);
        
        // 解析AST节点
        AstNode astNode = isDynamic ? parseAstNode(sqlElement) : null;
        
        return new SqlParseResult(
            sqlContent,
            SqlType.valueOf(sqlType),
            SqlSourceType.XML,
            isDynamic,
            parameters,
            astNode
        );
    }

    private void validateResultMapping(Element sqlElement) {
        if (!sqlElement.hasAttribute("resultMap")) {
            return;
        }

        String resultMap = sqlElement.getAttribute("resultMap").trim();
        throw new IllegalArgumentException(
            "Unsupported XML resultMap '" + resultMap + "'; use resultType, @UseRowMapper, or raw JDBC"
        );
    }

    private void validateSupportedTags(Element element) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) node;
            String tagName = childElement.getTagName().toLowerCase();
            if (!isSupportedDynamicTag(tagName)) {
                throw new IllegalArgumentException("Unsupported XML tag <" + tagName + ">");
            }
            validateSupportedTags(childElement);
        }
    }

    private boolean isSupportedDynamicTag(String tagName) {
        return switch (tagName) {
            case "if", "foreach", "choose", "when", "otherwise", "where", "set", "trim", "bind", "include" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取SQL内容
     */
    private String getSqlContent(Element sqlElement) {
        StringBuilder content = new StringBuilder();
        NodeList children = sqlElement.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                content.append(child.getTextContent());
            }
        }
        
        return content.toString().trim();
    }
    
    /**
     * 检查是否包含动态标签
     */
    private boolean containsDynamicTags(Element sqlElement) {
        return sqlElement.getElementsByTagName("if").getLength() > 0 ||
               sqlElement.getElementsByTagName("foreach").getLength() > 0 ||
               sqlElement.getElementsByTagName("choose").getLength() > 0 ||
               sqlElement.getElementsByTagName("where").getLength() > 0 ||
               sqlElement.getElementsByTagName("set").getLength() > 0 ||
               sqlElement.getElementsByTagName("trim").getLength() > 0;
    }
    
    /**
     * 解析AST节点 - 递归解析整个XML结构
     */
    private AstNode parseAstNode(Element sqlElement) {
        List<AstNode> children = parseChildNodes(sqlElement);
        if (children.isEmpty()) {
            return new AstNode.TextNode(getSqlContent(sqlElement));
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return new AstNode.ContainerNode(children);
    }
    
    /**
     * 递归解析子节点
     */
    private List<AstNode> parseChildNodes(Element element) {
        List<AstNode> nodes = new ArrayList<>();
        NodeList childNodes = element.getChildNodes();
        
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            
            if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent().trim();
                if (!text.isEmpty()) {
                    nodes.add(new AstNode.TextNode(text));
                }
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                AstNode astNode = parseElementNode(childElement);
                if (astNode != null) {
                    nodes.add(astNode);
                }
            }
        }
        
        return nodes;
    }
    
    /**
     * 解析元素节点，根据标签名返回对应的AstNode
     */
    private AstNode parseElementNode(Element element) {
        String tagName = element.getTagName().toLowerCase();
        
        switch (tagName) {
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
                throw new IllegalArgumentException("Unsupported XML tag <" + tagName + ">");
        }
    }
    
    /**
     * 解析IF元素
     */
    private AstNode parseIfElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildNodes(element);
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
        List<AstNode> children = parseChildNodes(element);
        return new AstNode.ForeachNode(collection, item, separator, open, close, children);
    }
    
    /**
     * 解析CHOOSE元素
     */
    private AstNode parseChooseElement(Element element) {
        List<AstNode> children = parseChildNodes(element);
        return new AstNode.ChooseNode(children);
    }
    
    /**
     * 解析WHEN元素
     */
    private AstNode parseWhenElement(Element element) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildNodes(element);
        return new AstNode.WhenNode(test, children);
    }
    
    /**
     * 解析OTHERWISE元素
     */
    private AstNode parseOtherwiseElement(Element element) {
        List<AstNode> children = parseChildNodes(element);
        return new AstNode.OtherwiseNode(children);
    }
    
    /**
     * 解析WHERE元素
     */
    private AstNode parseWhereElement(Element element) {
        List<AstNode> children = parseChildNodes(element);
        return new AstNode.WhereNode(children);
    }
    
    /**
     * 解析SET元素
     */
    private AstNode parseSetElement(Element element) {
        List<AstNode> children = parseChildNodes(element);
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
        List<AstNode> children = parseChildNodes(element);
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
        
        // 尝试从缓存中获取片段
        String xmlPath = currentXmlPath.get();
        if (xmlPath != null) {
            Map<String, Element> fragments = sqlFragmentCache.get(xmlPath);
            if (fragments != null) {
                Element fragmentElement = fragments.get(refId);
                if (fragmentElement != null) {
                    // 递归解析片段内容
                    List<AstNode> fragmentNodes = parseChildNodes(fragmentElement);
                    // 如果只有一个节点，直接返回
                    if (fragmentNodes.size() == 1) {
                        return fragmentNodes.get(0);
                    }
                    // 多个节点，需要合并（这里简化处理，实际可能需要一个容器节点）
                    StringBuilder content = new StringBuilder();
                    for (AstNode node : fragmentNodes) {
                        if (node instanceof AstNode.TextNode) {
                            content.append(((AstNode.TextNode) node).text()).append(" ");
                        }
                    }
                    return new AstNode.TextNode(content.toString().trim());
                }
            }
        }
        
        // 如果找不到片段，返回IncludeNode（让后续处理决定如何处理）
        return new AstNode.IncludeNode(refId);
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
}

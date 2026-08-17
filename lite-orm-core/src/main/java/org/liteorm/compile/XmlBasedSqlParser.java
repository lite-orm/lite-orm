package org.liteorm.compile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.processing.Filer;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses Mapper XML into the normalized dynamic SQL model.
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
final class XmlBasedSqlParser implements SqlContentParser {

    private final Filer filer;
    private final Map<String, XmlResource> xmlCache = new ConcurrentHashMap<>();

    public XmlBasedSqlParser() {
        this(null);
    }

    public XmlBasedSqlParser(Filer filer) {
        this.filer = filer;
    }
    
    @Override
    public SqlParseResult parseSql(ExecutableElement method) {
        String xmlPath = getXmlPath(method);
        if (xmlPath == null) {
            return null;
        }

        XmlResource xmlResource = getXmlResource(xmlPath);
        if (xmlResource == null) {
            return null;
        }

        String methodName = method.getSimpleName().toString();
        Element sqlElement = findSqlElement(xmlResource.document(), methodName);
        if (sqlElement == null) {
            return null;
        }

        ParseContext context = new ParseContext(xmlResource.fragments(), new ArrayDeque<>());
        return parseSqlElement(sqlElement, method, context);
    }
    
    @Override
    public boolean supports(ExecutableElement method) {
        return getXmlPath(method) != null;
    }

    public boolean hasMapperResource(ExecutableElement method) {
        String xmlPath = getXmlPath(method);
        return xmlPath != null && getXmlResource(xmlPath) != null;
    }
    
    @Override
    public String getParserName() {
        return "XmlBasedSqlParser";
    }
    
    /**
     * Returns the conventional Mapper XML resource path.
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
     * Loads and caches a Mapper XML resource.
     */
    private XmlResource getXmlResource(String xmlPath) {
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
                Document document = builder.parse(is);
                return new XmlResource(document, collectSqlFragments(document));
            } catch (ParserConfigurationException | SAXException | IOException e) {
                System.err.println("XML parsing failed: " + path + ", " + e.getMessage());
                return null;
            }
        });
    }

    private InputStream openXmlStream(String xmlPath) throws IOException {
        String relativePath = xmlPath.startsWith("/") ? xmlPath.substring(1) : xmlPath;
        if (filer != null) {
            for (StandardLocation location : List.of(
                    StandardLocation.CLASS_PATH,
                    StandardLocation.CLASS_OUTPUT,
                    StandardLocation.SOURCE_PATH)) {
                try {
                    return filer.getResource(location, "", relativePath).openInputStream();
                } catch (IOException | IllegalArgumentException ignored) {
                    // Try the next location and then the standalone fallbacks.
                }
            }
        }

        InputStream classpathStream = getClass().getResourceAsStream(xmlPath);
        if (classpathStream != null) {
            return classpathStream;
        }

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
     * Collects named SQL fragments.
     */
    private Map<String, Element> collectSqlFragments(Document document) {
        Map<String, Element> fragments = new HashMap<>();
        NodeList sqlNodes = document.getElementsByTagName("sql");
        
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElement = (Element) sqlNodes.item(i);
            String id = sqlElement.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                fragments.put(id, sqlElement);
            }
        }
        
        return Map.copyOf(fragments);
    }
    
    /**
     * Finds a statement element by identifier.
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
     * Parses one statement element.
     */
    private SqlParseResult parseSqlElement(
            Element sqlElement, ExecutableElement method, ParseContext context) {
        validateStatementAttributes(sqlElement);
        validateResultMapping(sqlElement);
        validateSupportedTags(sqlElement);
        String sqlType = sqlElement.getTagName().toUpperCase();
        String sqlContent = getSqlContent(sqlElement);
        boolean isDynamic = containsDynamicTags(sqlElement);
        
        List<ParameterInfo> parameters = parseParameters(method);
        
        AstNode astNode = isDynamic ? parseAstNode(sqlElement, context) : null;
        
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

    private void validateStatementAttributes(Element sqlElement) {
        Set<String> allowedAttributes = "select".equalsIgnoreCase(sqlElement.getTagName())
            ? Set.of("id", "resultType", "resultMap")
            : Set.of("id");
        validateElementAttributes(sqlElement, allowedAttributes, Set.of("id"));
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
            validateDynamicElementAttributes(childElement, tagName);
            validateSupportedTags(childElement);
        }
    }

    private void validateDynamicElementAttributes(Element element, String tagName) {
        switch (tagName) {
            case "if", "when" -> validateElementAttributes(element, Set.of("test"), Set.of("test"));
            case "foreach" -> validateElementAttributes(
                element, Set.of("collection", "item", "separator", "open", "close"),
                Set.of("collection", "item"));
            case "trim" -> validateElementAttributes(
                element, Set.of("prefix", "suffix", "prefixOverrides", "suffixOverrides"), Set.of());
            case "bind" -> validateElementAttributes(element, Set.of("name", "value"), Set.of("name", "value"));
            case "include" -> validateElementAttributes(element, Set.of("refid"), Set.of("refid"));
            case "choose" -> {
                validateElementAttributes(element, Set.of(), Set.of());
                validateChooseStructure(element);
            }
            case "otherwise", "where", "set" -> validateElementAttributes(element, Set.of(), Set.of());
            default -> throw new IllegalArgumentException("Unsupported XML tag <" + tagName + ">");
        }
    }

    private void validateChooseStructure(Element chooseElement) {
        boolean otherwiseSeen = false;
        NodeList children = chooseElement.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String childName = ((Element) child).getTagName().toLowerCase();
            if ("when".equals(childName)) {
                if (otherwiseSeen) {
                    throw new IllegalArgumentException("XML <otherwise> must be the last child of <choose>");
                }
                continue;
            }
            if ("otherwise".equals(childName)) {
                if (otherwiseSeen) {
                    throw new IllegalArgumentException("XML <choose> supports at most one <otherwise>");
                }
                otherwiseSeen = true;
                continue;
            }
            throw new IllegalArgumentException(
                "XML <choose> only supports <when> and <otherwise> children"
            );
        }
    }

    private void validateElementAttributes(Element element, Set<String> allowed, Set<String> required) {
        var attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            String attributeName = attributes.item(index).getNodeName();
            if (!allowed.contains(attributeName)) {
                throw new IllegalArgumentException(
                    "Unsupported XML attribute '" + attributeName + "' on <" + element.getTagName() + ">"
                );
            }
        }
        for (String attributeName : required) {
            if (!element.hasAttribute(attributeName) || element.getAttribute(attributeName).isBlank()) {
                throw new IllegalArgumentException(
                    "XML <" + element.getTagName() + "> requires non-blank attribute '" + attributeName + "'"
                );
            }
        }
    }

    private boolean isSupportedDynamicTag(String tagName) {
        return switch (tagName) {
            case "if", "foreach", "choose", "when", "otherwise", "where", "set", "trim", "bind", "include" -> true;
            default -> false;
        };
    }
    
    /**
     * Returns text content for a statement element.
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
     * Returns whether the statement contains dynamic tags.
     */
    private boolean containsDynamicTags(Element sqlElement) {
        return sqlElement.getElementsByTagName("if").getLength() > 0 ||
               sqlElement.getElementsByTagName("foreach").getLength() > 0 ||
               sqlElement.getElementsByTagName("choose").getLength() > 0 ||
               sqlElement.getElementsByTagName("where").getLength() > 0 ||
               sqlElement.getElementsByTagName("set").getLength() > 0 ||
               sqlElement.getElementsByTagName("trim").getLength() > 0 ||
               sqlElement.getElementsByTagName("bind").getLength() > 0 ||
               sqlElement.getElementsByTagName("include").getLength() > 0;
    }
    
    /**
     * Recursively parses the statement AST.
     */
    private AstNode parseAstNode(Element sqlElement, ParseContext context) {
        List<AstNode> children = parseChildNodes(sqlElement, context);
        if (children.isEmpty()) {
            return new AstNode.TextNode(getSqlContent(sqlElement));
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return new AstNode.ContainerNode(children);
    }
    
    /**
     * Recursively parses child nodes.
     */
    private List<AstNode> parseChildNodes(Element element, ParseContext context) {
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
                AstNode astNode = parseElementNode(childElement, context);
                if (astNode != null) {
                    nodes.add(astNode);
                }
            }
        }
        
        return nodes;
    }
    
    /**
     * Parses an element into its corresponding AST node.
     */
    private AstNode parseElementNode(Element element, ParseContext context) {
        String tagName = element.getTagName().toLowerCase();
        
        switch (tagName) {
            case "if":
                return parseIfElement(element, context);
            case "foreach":
                return parseForeachElement(element, context);
            case "choose":
                return parseChooseElement(element, context);
            case "when":
                return parseWhenElement(element, context);
            case "otherwise":
                return parseOtherwiseElement(element, context);
            case "where":
                return parseWhereElement(element, context);
            case "set":
                return parseSetElement(element, context);
            case "trim":
                return parseTrimElement(element, context);
            case "bind":
                return parseBindElement(element);
            case "include":
                return parseIncludeElement(element, context);
            default:
                throw new IllegalArgumentException("Unsupported XML tag <" + tagName + ">");
        }
    }
    
    /**
     * Parses an {@code if} element.
     */
    private AstNode parseIfElement(Element element, ParseContext context) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.IfNode(test, children);
    }
    
    /**
     * Parses a {@code foreach} element.
     */
    private AstNode parseForeachElement(Element element, ParseContext context) {
        String collection = element.getAttribute("collection");
        String item = element.getAttribute("item");
        String separator = element.getAttribute("separator");
        String open = element.getAttribute("open");
        String close = element.getAttribute("close");
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.ForeachNode(collection, item, separator, open, close, children);
    }
    
    /**
     * Parses a {@code choose} element.
     */
    private AstNode parseChooseElement(Element element, ParseContext context) {
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.ChooseNode(children);
    }
    
    /**
     * Parses a {@code when} element.
     */
    private AstNode parseWhenElement(Element element, ParseContext context) {
        String test = element.getAttribute("test");
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.WhenNode(test, children);
    }
    
    /**
     * Parses an {@code otherwise} element.
     */
    private AstNode parseOtherwiseElement(Element element, ParseContext context) {
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.OtherwiseNode(children);
    }
    
    /**
     * Parses a {@code where} element.
     */
    private AstNode parseWhereElement(Element element, ParseContext context) {
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.WhereNode(children);
    }
    
    /**
     * Parses a {@code set} element.
     */
    private AstNode parseSetElement(Element element, ParseContext context) {
        List<AstNode> children = parseChildNodes(element, context);
        return new AstNode.SetNode(children);
    }
    
    /**
     * Parses a {@code trim} element.
     */
    private AstNode parseTrimElement(Element element, ParseContext context) {
        String prefix = element.getAttribute("prefix");
        String suffix = element.getAttribute("suffix");
        String prefixOverrides = element.getAttribute("prefixOverrides");
        String suffixOverrides = element.getAttribute("suffixOverrides");
        List<AstNode> children = parseChildNodes(element, context);
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
    private AstNode parseIncludeElement(Element element, ParseContext context) {
        String refId = element.getAttribute("refid");

        Element fragmentElement = context.fragments().get(refId);
        if (fragmentElement == null) {
            throw new IllegalArgumentException("Unknown XML <include> refid '" + refId + "'");
        }
        Deque<String> includePath = context.includePath();
        if (includePath.contains(refId)) {
            List<String> cycle = new ArrayList<>(includePath);
            cycle.add(refId);
            throw new IllegalArgumentException(
                "Cyclic XML <include> reference: " + String.join(" -> ", cycle)
            );
        }
        includePath.addLast(refId);
        try {
            validateSupportedTags(fragmentElement);
            List<AstNode> fragmentNodes = parseChildNodes(fragmentElement, context);
            if (fragmentNodes.size() == 1) {
                return fragmentNodes.get(0);
            }
            return new AstNode.ContainerNode(fragmentNodes);
        } finally {
            includePath.removeLast();
        }
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

    private record XmlResource(Document document, Map<String, Element> fragments) {
    }

    private record ParseContext(Map<String, Element> fragments, Deque<String> includePath) {
    }
}

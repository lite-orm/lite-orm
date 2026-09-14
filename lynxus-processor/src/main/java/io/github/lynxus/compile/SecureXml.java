package io.github.lynxus.compile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Creates hardened, offline-only DOM parsers for compile-time SQL sources.
 */
final class SecureXml {

    private static final String XINCLUDE_NAMESPACE = "http://www.w3.org/2001/XInclude";
    private static final String XML_SCHEMA_INSTANCE_NAMESPACE =
        "http://www.w3.org/2001/XMLSchema-instance";

    private SecureXml() {
    }

    static Document parse(InputStream input, String source) {
        try {
            return parse(input.readAllBytes(), source);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                "failed to read " + source + ": " + exception.getMessage(), exception);
        }
    }

    static Document parse(String xml, String source) {
        return parse(xml.getBytes(StandardCharsets.UTF_8), source);
    }

    private static Document parse(byte[] xml, String source) {
        if (containsDoctypeDeclaration(new String(xml, StandardCharsets.ISO_8859_1))) {
            throw new IllegalArgumentException("DOCTYPE is not allowed in " + source);
        }

        DocumentBuilder builder = newDocumentBuilder(source);
        try {
            Document document = builder.parse(new ByteArrayInputStream(xml));
            validateNoExternalDeclarations(document, source);
            return document;
        } catch (SAXException | IOException exception) {
            throw new IllegalArgumentException(
                "failed to parse " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static boolean containsDoctypeDeclaration(String xml) {
        String upperXml = xml.toUpperCase(Locale.ROOT);
        for (int index = 0; index < upperXml.length(); index++) {
            if (upperXml.startsWith("<!--", index)) {
                int commentEnd = upperXml.indexOf("-->", index + 4);
                index = commentEnd < 0 ? upperXml.length() : commentEnd + 2;
            } else if (upperXml.startsWith("<![CDATA[", index)) {
                int cdataEnd = upperXml.indexOf("]]>", index + 9);
                index = cdataEnd < 0 ? upperXml.length() : cdataEnd + 2;
            } else if (upperXml.startsWith("<!DOCTYPE", index)) {
                return true;
            }
        }
        return false;
    }

    private static DocumentBuilder newDocumentBuilder(String source) {
        return newDocumentBuilder(DocumentBuilderFactory.newInstance(), source);
    }

    static DocumentBuilder newDocumentBuilder(DocumentBuilderFactory factory, String source) {
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("external XML resolution is disabled for " + source);
            });
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            return builder;
        } catch (ParserConfigurationException | RuntimeException exception) {
            throw new IllegalStateException(
                "failed to configure secure XML parsing for " + source + ": " + exception.getMessage(),
                exception);
        }
    }

    private static void validateNoExternalDeclarations(Document document, String source) {
        if (document.getDoctype() != null) {
            throw new IllegalArgumentException("DOCTYPE is not allowed in " + source);
        }
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (XINCLUDE_NAMESPACE.equals(element.getNamespaceURI())) {
                throw new IllegalArgumentException("XInclude is not allowed in " + source);
            }
            NamedNodeMap attributes = element.getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Node attribute = attributes.item(attributeIndex);
                if (XML_SCHEMA_INSTANCE_NAMESPACE.equals(attribute.getNamespaceURI())
                        && ("schemaLocation".equals(attribute.getLocalName())
                            || "noNamespaceSchemaLocation".equals(attribute.getLocalName()))) {
                    throw new IllegalArgumentException(
                        "external schema declarations are not allowed in " + source);
                }
            }
        }
    }
}

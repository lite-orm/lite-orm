package io.github.lynxus.compile;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureXmlTest {

    @Test
    void failsClosedWhenARequiredSecurityControlIsUnsupported() {
        DocumentBuilderFactory factory = new DocumentBuilderFactory() {
            @Override
            public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
                throw new ParserConfigurationException("builder must not be created");
            }

            @Override
            public void setAttribute(String name, Object value) {
            }

            @Override
            public Object getAttribute(String name) {
                return null;
            }

            @Override
            public void setFeature(String name, boolean value) throws ParserConfigurationException {
                throw new ParserConfigurationException("unsupported control: " + name);
            }

            @Override
            public boolean getFeature(String name) {
                return false;
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
            SecureXml.newDocumentBuilder(factory, "test Mapper XML"));

        assertTrue(failure.getMessage().contains("failed to configure secure XML parsing"));
        assertTrue(failure.getMessage().contains("test Mapper XML"));
        assertTrue(failure.getMessage().contains("unsupported control"));
    }
}

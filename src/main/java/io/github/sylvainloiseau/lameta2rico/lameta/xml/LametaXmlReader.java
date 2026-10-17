package io.github.sylvainloiseau.lameta2rico.lameta.xml;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;

/**
 * Reads a Lameta/SayMore metadata file ({@code .sprj}, {@code .session}, {@code .person},
 * {@code .meta}) into an {@link XmlElement} tree.
 *
 * <p>Leading whitespace before the XML declaration (found in some sample files) is removed,
 * DOCTYPE declarations are refused and external entities are never resolved. The
 * {@code type} attribute Lameta puts on most elements ({@code type="string"},
 * {@code type="xml"}) is dropped.
 */
public final class LametaXmlReader {

    private static final String IGNORED_ATTRIBUTE = "type";

    private LametaXmlReader() {
    }

    /**
     * Reads {@code file}.
     *
     * @throws RdfConversionException if the file cannot be read, is empty or is not well-formed
     */
    public static XmlElement read(Path file) {
        return readIfNotBlank(file)
                .orElseThrow(() -> new RdfConversionException("Empty metadata file: " + file));
    }

    /**
     * Reads {@code file}, or returns empty if it contains only whitespace (Lameta leaves empty
     * {@code .meta} files behind).
     *
     * @throws RdfConversionException if the file cannot be read or is not well-formed
     */
    public static Optional<XmlElement> readIfNotBlank(Path file) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RdfConversionException("Cannot read " + file, e);
        }
        if (content.replace("\uFEFF", "").isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(content));
        } catch (RdfConversionException e) {
            throw new RdfConversionException("Malformed XML in " + file + ": " + e.getMessage(), e.getCause());
        }
    }

    public static XmlElement parse(String content) {
        String xml = content.stripLeading();
        if (!xml.isEmpty() && xml.charAt(0) == '﻿') {
            xml = xml.substring(1).stripLeading();
        }
        try {
            DocumentBuilder builder = newFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler()); // no "[Fatal Error]" lines on stderr
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            return convert(root);
        } catch (SAXException | IOException e) {
            throw new RdfConversionException(e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        return f;
    }

    private static XmlElement convert(Element element) {
        Map<String, String> attributes = new LinkedHashMap<>();
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (!a.getNodeName().equals(IGNORED_ATTRIBUTE)) {
                attributes.put(a.getNodeName(), a.getNodeValue());
            }
        }
        StringBuilder text = new StringBuilder();
        List<XmlElement> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            switch (n.getNodeType()) {
                case Node.ELEMENT_NODE -> children.add(convert((Element) n));
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(n.getNodeValue());
                default -> {
                    // comments and processing instructions are ignored
                }
            }
        }
        return new XmlElement(element.getTagName(), attributes, text.toString(), children);
    }
}

package fr.cnrs.lacito.lameta2rdf.lameta.xml;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal immutable XML element: name, attributes, trimmed text content and child elements.
 * Lameta metadata files are simple records, so mixed content is not represented: {@code text}
 * holds the element's own text nodes only.
 */
public record XmlElement(String name, Map<String, String> attributes, String text, List<XmlElement> children) {

    public XmlElement {
        attributes = Map.copyOf(attributes);
        text = text == null ? "" : text.strip();
        children = List.copyOf(children);
    }

    public static XmlElement empty(String name) {
        return new XmlElement(name, Map.of(), "", List.of());
    }

    /** First child with this name. */
    public Optional<XmlElement> child(String childName) {
        return children.stream().filter(c -> c.name.equals(childName)).findFirst();
    }

    public List<XmlElement> children(String childName) {
        return children.stream().filter(c -> c.name.equals(childName)).toList();
    }

    /** Non-blank text of the first child with this name. */
    public Optional<String> childText(String childName) {
        return child(childName).map(XmlElement::text).filter(t -> !t.isEmpty());
    }

    public boolean hasText() {
        return !text.isEmpty();
    }
}

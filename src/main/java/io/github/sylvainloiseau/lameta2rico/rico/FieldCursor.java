package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;

/**
 * Reads the child fields of a metadata element and remembers which ones were used, so that
 * the remaining ones can be handed to {@link UnmappedFieldHandler}.
 */
final class FieldCursor {

    private final XmlElement element;
    private final Set<String> consumed = new HashSet<>();

    FieldCursor(XmlElement element) {
        this.element = element;
    }

    /** Non-blank text of the first child named {@code name}; marks the name as used. */
    Optional<String> text(String name) {
        consumed.add(name);
        return element.childText(name);
    }

    /** First child named {@code name}; marks the name as used. */
    Optional<XmlElement> child(String name) {
        consumed.add(name);
        return element.child(name);
    }

    /** Non-blank texts of the children whose name matches; marks those names as used. */
    List<String> texts(Predicate<String> nameFilter) {
        return element.children().stream()
                .filter(c -> nameFilter.test(c.name()))
                .peek(c -> consumed.add(c.name()))
                .map(XmlElement::text)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    void consume(String... names) {
        consumed.addAll(List.of(names));
    }

    /** Children not used so far, in document order. */
    List<XmlElement> remaining() {
        return element.children().stream().filter(c -> !consumed.contains(c.name())).toList();
    }
}

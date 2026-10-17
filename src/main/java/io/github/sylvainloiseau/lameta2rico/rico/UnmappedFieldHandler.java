package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;

/**
 * Fallback for metadata fields with no RiC-O equivalent (plan §5.8): either a
 * {@code rico:generalDescription "<label>: <value>"} statement, or nothing. Each distinct field
 * name is logged once.
 */
final class UnmappedFieldHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UnmappedFieldHandler.class);

    private final UnmappedPolicy policy;
    private final RdfSink sink;
    private final Set<String> logged = new HashSet<>();

    UnmappedFieldHandler(UnmappedPolicy policy, RdfSink sink) {
        this.policy = policy;
        this.sink = sink;
    }

    /** Lameta fields: the label is the humanized element name ({@code fathersLanguage} → "Fathers language"). */
    void handle(IRI subject, List<XmlElement> fields) {
        fields.forEach(f -> handle(subject, f, false));
    }

    /** User-defined fields ({@code CustomFields} children): the label is the element name as is. */
    void handleCustomFields(IRI subject, XmlElement customFields) {
        customFields.children().forEach(f -> handle(subject, f, true));
    }

    private void handle(IRI subject, XmlElement field, boolean verbatimLabel) {
        if (!field.children().isEmpty()) {
            field.children().forEach(c -> handle(subject, c, verbatimLabel));
            return;
        }
        if (!field.hasText()) {
            return;
        }
        if (logged.add(field.name())) {
            LOG.info("No RiC-O mapping for field '{}'{}", field.name(),
                    policy == UnmappedPolicy.DROP ? "; dropped" : "; kept as rico:generalDescription");
        }
        if (policy == UnmappedPolicy.DESCRIPTION) {
            String label = verbatimLabel ? field.name() : humanize(field.name());
            sink.literal(subject, RICO.generalDescription, label + ": " + field.text());
        }
    }

    /** {@code primaryLanguageLearnedIn} → "Primary language learned in"; {@code Social_Context} → "Social context". */
    static String humanize(String name) {
        String spaced = name.replace('_', ' ').replace('-', ' ')
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("\\s+", " ")
                .strip()
                .toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? name : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}

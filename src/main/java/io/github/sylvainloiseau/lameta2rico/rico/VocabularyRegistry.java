package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.model.IRI;

import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;

/**
 * Get-or-create access to shared nodes: controlled values (genres, roles, demographic
 * groups...), languages and places. IRIs are deterministic (plan §4), and each node's
 * description is emitted through the {@link RdfSink}, which drops repeats.
 */
final class VocabularyRegistry {

    /** ISO 639-1 / 639-3 codes as written in Lameta files. */
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2,3}");

    private final UriFactory uris;
    private final RdfSink sink;
    private final Map<String, String> nameByCode = new HashMap<>();
    private final Map<String, String> codeByLowerName = new HashMap<>();

    VocabularyRegistry(UriFactory uris, RdfSink sink) {
        this.uris = uris;
        this.sink = sink;
    }

    /** A controlled-value node: {@code a <type>; rico:name value}. */
    IRI term(String kind, String facet, String value, IRI type) {
        IRI node = uris.vocabulary(kind, facet, value);
        sink.type(node, type);
        sink.literal(node, RICO.name, value);
        return node;
    }

    // ---- languages

    /**
     * Records a {@code "code : Name"} pair (project file) so that later references by name
     * ({@code Tuwari}) resolve to the coded node; returns the language node.
     */
    Optional<IRI> registerCodeAndName(String value) {
        int sep = value.indexOf(':');
        if (sep < 0) {
            return Optional.of(language(value));
        }
        String code = value.substring(0, sep).strip();
        String name = value.substring(sep + 1).strip();
        if (code.isEmpty()) {
            return name.isEmpty() ? Optional.empty() : Optional.of(language(name));
        }
        if (!name.isEmpty()) {
            nameByCode.putIfAbsent(code, name);
            codeByLowerName.putIfAbsent(name.toLowerCase(Locale.ROOT), code);
        }
        return Optional.of(language(code));
    }

    /** Language node for a code ({@code tpi}) or a name ({@code Tuwari}). */
    IRI language(String codeOrName) {
        String v = codeOrName.strip();
        String code = LANGUAGE_CODE.matcher(v).matches() ? v : codeByLowerName.get(v.toLowerCase(Locale.ROOT));
        if (code != null) {
            IRI node = uris.language(code);
            sink.type(node, RICO.Language);
            sink.literal(node, RICO.identifier, code);
            sink.literal(node, RICO.name, nameByCode.get(code));
            return node;
        }
        IRI node = uris.languageByName(v);
        sink.type(node, RICO.Language);
        sink.literal(node, RICO.name, v);
        return node;
    }

    // ---- places

    /**
     * Builds a chain of places from the most specific to the most general, linked by
     * {@code rico:isOrWasPartOf}; blank names are skipped. The first name, if
     * {@code firstIsLocation}, is a free-text location (address) and also gets
     * {@code rico:location}.
     *
     * @return the most specific place, if any name was given
     */
    Optional<IRI> placeChain(boolean firstIsLocation, String... namesMostSpecificFirst) {
        IRI first = null;
        IRI previous = null;
        for (int i = 0; i < namesMostSpecificFirst.length; i++) {
            String name = namesMostSpecificFirst[i];
            if (name == null || name.isBlank()) {
                continue;
            }
            IRI place = uris.vocabulary("place", null, name);
            sink.type(place, RICO.Place);
            sink.literal(place, RICO.name, name);
            if (i == 0 && firstIsLocation) {
                sink.literal(place, RICO.location, name);
            }
            if (previous != null) {
                sink.add(previous, RICO.isOrWasPartOf, place);
            } else {
                first = place;
            }
            previous = place;
        }
        return Optional.ofNullable(first);
    }
}

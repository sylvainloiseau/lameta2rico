package io.github.sylvainloiseau.lameta2rico.rico;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sylvainloiseau.lameta2rico.lameta.model.Folder;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;

/**
 * Resolves the person references found in contributions, participants and project fields to
 * the {@code rico:Person} nodes of the {@code People} folder (plan §5.7): by code first, then by
 * name (exact, then case-insensitive). Unknown references get a minted node and a warning.
 */
final class AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRegistry.class);

    private final UriFactory uris;
    private final RdfSink sink;
    private final Map<String, IRI> byCode = new HashMap<>();
    private final Map<String, IRI> byName = new HashMap<>();
    private final Map<String, IRI> byLowerName = new HashMap<>();
    private final Map<Path, IRI> byFolder = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    AgentRegistry(UriFactory uris, RdfSink sink) {
        this.uris = uris;
        this.sink = sink;
    }

    /** Registers a person folder; its IRI is {@code {P}/person/{code}}, or the folder name without code. */
    IRI register(Folder person) {
        String code = person.metadata().childText("code").orElse(null);
        String name = person.metadata().childText("name").orElse(person.dirName());
        IRI iri = uris.person(code != null ? code : person.dirName());
        byFolder.put(person.dir(), iri);
        if (code != null) {
            IRI previous = byCode.putIfAbsent(code, iri);
            if (previous != null && !previous.equals(iri)) {
                LOG.warn("Two people share the code '{}'; references resolve to the first", code);
            }
        }
        byName.putIfAbsent(name, iri);
        byName.putIfAbsent(person.dirName(), iri);
        byLowerName.putIfAbsent(name.toLowerCase(Locale.ROOT), iri);
        return iri;
    }

    IRI iriOf(Folder person) {
        return byFolder.get(person.dir());
    }

    /** Resolves a reference; unknown ones become {@code {P}/person/{value}} ({@code rico:Person}). */
    IRI resolvePerson(String reference) {
        return resolve(reference, true);
    }

    /** Resolves a reference; unknown ones become {@code {P}/agent/{value}} ({@code rico:Agent}). */
    IRI resolveAgent(String reference) {
        return resolve(reference, false);
    }

    private IRI resolve(String reference, boolean person) {
        String ref = reference.strip();
        IRI iri = byCode.get(ref);
        if (iri == null) {
            iri = byName.get(ref);
        }
        if (iri == null) {
            iri = byLowerName.get(ref.toLowerCase(Locale.ROOT));
        }
        if (iri != null) {
            return iri;
        }
        if (warned.add(ref)) {
            LOG.warn("'{}' matches no person of the People folder; creating a separate {} node", ref,
                    person ? "rico:Person" : "rico:Agent");
        }
        IRI minted = person ? uris.person(ref) : uris.agent(ref);
        sink.type(minted, person ? RICO.Person : RICO.Agent);
        sink.literal(minted, RICO.name, ref);
        return minted;
    }
}

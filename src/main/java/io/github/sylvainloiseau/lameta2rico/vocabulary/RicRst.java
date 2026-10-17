package io.github.sylvainloiseau.lameta2rico.vocabulary;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;

/**
 * Record set types from the RiC vocabulary {@code recordSetTypes}, used as values of
 * {@code rico:hasRecordSetType}. This vocabulary is separate from RiC-O and is referenced by
 * IRI only.
 */
public final class RicRst {

    public static final String NAMESPACE = "https://www.ica.org/standards/RiC/vocabularies/recordSetTypes#";
    public static final String PREFIX = "ric-rst";
    public static final Namespace NS = new SimpleNamespace(PREFIX, NAMESPACE);

    public static final IRI Fonds = Values.iri(NAMESPACE, "Fonds");
    public static final IRI Series = Values.iri(NAMESPACE, "Series");
    public static final IRI File = Values.iri(NAMESPACE, "File");

    private RicRst() {
    }
}

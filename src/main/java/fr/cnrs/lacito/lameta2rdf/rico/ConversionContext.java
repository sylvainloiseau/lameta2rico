package fr.cnrs.lacito.lameta2rdf.rico;

import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.rio.RDFHandler;

/** State shared by the mappers during one conversion run. */
final class ConversionContext {

    final UriFactory uris;
    final RdfSink sink;
    final VocabularyRegistry vocabulary;
    final AgentRegistry agents;
    final UnmappedFieldHandler unmapped;
    final boolean includeContactInfo;

    /** Rule of the project's access protocol (e.g. PARADISEC), parent of the session access codes. */
    Optional<IRI> accessProtocol = Optional.empty();

    ConversionContext(UriFactory uris, RDFHandler handler, UnmappedPolicy policy,
            boolean includeContactInfo) {
        this.uris = uris;
        this.sink = new RdfSink(handler);
        this.vocabulary = new VocabularyRegistry(uris, sink);
        this.agents = new AgentRegistry(uris, sink);
        this.unmapped = new UnmappedFieldHandler(policy, sink);
        this.includeContactInfo = includeContactInfo;
    }
}

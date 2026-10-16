package fr.cnrs.lacito.lameta2rdf.rico;

import java.util.List;

import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFHandler;

import fr.cnrs.lacito.lameta2rdf.lameta.model.Folder;
import fr.cnrs.lacito.lameta2rdf.lameta.model.LametaProject;
import fr.cnrs.lacito.lameta2rdf.vocabulary.RICO;
import fr.cnrs.lacito.lameta2rdf.vocabulary.RicRst;

/** Streams the RiC-O graph of a {@link LametaProject} into an {@link RDFHandler}. */
public final class RicoGraphBuilder {

    private final UriFactory uris;
    private final UnmappedPolicy unmappedPolicy;
    private final boolean includeContactInfo;

    public RicoGraphBuilder(UriFactory uris, UnmappedPolicy unmappedPolicy, boolean includeContactInfo) {
        this.uris = uris;
        this.unmappedPolicy = unmappedPolicy;
        this.includeContactInfo = includeContactInfo;
    }

    /** Prefixes declared before any statement. */
    public List<Namespace> namespaces() {
        return List.of(RICO.NS, RicRst.NS, SKOS.NS, XSD.NS, RDF.NS, RDFS.NS,
                new SimpleNamespace("mydata", uris.base() + "/"));
    }

    /** Calls {@code startRDF}, declares the namespaces, emits the graph and calls {@code endRDF}. */
    public void build(LametaProject project, RDFHandler handler) {
        handler.startRDF();
        for (Namespace ns : namespaces()) {
            handler.handleNamespace(ns.getPrefix(), ns.getName());
        }

        ConversionContext ctx = new ConversionContext(uris, handler, unmappedPolicy, includeContactInfo);
        DocumentMapper documents = new DocumentMapper(ctx);
        ProjectMapper projectMapper = new ProjectMapper(ctx, documents);
        PersonMapper personMapper = new PersonMapper(ctx, documents);
        SessionMapper sessionMapper = new SessionMapper(ctx, documents);

        // Order matters: language names, then people (for name resolution), then the rest.
        projectMapper.registerLanguages(project.metadata());
        project.people().forEach(ctx.agents::register);
        for (Folder person : project.people()) {
            personMapper.map(person);
        }
        projectMapper.map(project);
        for (Folder session : project.sessions()) {
            sessionMapper.map(session);
        }

        handler.endRDF();
    }
}

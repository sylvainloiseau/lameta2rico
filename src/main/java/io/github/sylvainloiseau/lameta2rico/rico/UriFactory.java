package io.github.sylvainloiseau.lameta2rico.rico;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.util.Values;

/**
 * Mints every IRI of the output (plan §4). {@code base} is the corpus URI prefix without
 * trailing slash; path segments are percent-encoded (UTF-8, space as {@code %20}).
 */
public final class UriFactory {

    private final String base;
    private final String project;

    public UriFactory(String base, String projectDirName) {
        this.base = base;
        this.project = base + "/project/" + encode(projectDirName);
    }

    public String base() {
        return base;
    }

    public IRI project() {
        return Values.iri(project);
    }

    /** Record set of a project folder: {@code Sessions}, {@code People}, {@code DescriptionDocuments}... */
    public IRI projectFolder(String folderName) {
        return Values.iri(project + "/" + encode(folderName));
    }

    public IRI session(String dirName) {
        return Values.iri(base + "/session/" + encode(dirName));
    }

    public IRI sessionActivity(String dirName) {
        return Values.iri(session(dirName).stringValue() + "/activity");
    }

    public IRI person(String key) {
        return Values.iri(base + "/person/" + encode(key));
    }

    public IRI agent(String name) {
        return Values.iri(base + "/agent/" + encode(name));
    }

    public IRI personDocuments(String dirName) {
        return Values.iri(base + "/person/" + encode(dirName) + "/documents");
    }

    public IRI nickname(IRI person) {
        return Values.iri(person.stringValue() + "/name/nickname");
    }

    public IRI record(IRI container, String fileName) {
        return Values.iri(container.stringValue() + "/record/" + encode(fileName));
    }

    public IRI instantiation(IRI container, String fileName) {
        return Values.iri(container.stringValue() + "/instantiation/" + encode(fileName));
    }

    public IRI creation(IRI source, int rank) {
        return Values.iri(source.stringValue() + "/creation/" + rank);
    }

    public IRI language(String code) {
        return Values.iri(base + "/language/" + encode(code));
    }

    public IRI languageByName(String name) {
        return Values.iri(base + "/language/name/" + encode(name));
    }

    /** Controlled-value node {@code {P}/{kind}/{facet}/{value}}, or {@code {P}/{kind}/{value}} when facet is null. */
    public IRI vocabulary(String kind, String facet, String value) {
        return Values.iri(base + "/" + kind + (facet == null ? "" : "/" + encode(facet)) + "/" + encode(value));
    }

    static String encode(String segment) {
        return URLEncoder.encode(segment.strip(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}

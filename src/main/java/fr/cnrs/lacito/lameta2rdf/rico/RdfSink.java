package fr.cnrs.lacito.lameta2rdf.rico;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFHandler;

/**
 * Statement output of one conversion run. Wraps the caller's {@link RDFHandler} and drops
 * statements already sent, so that shared nodes (languages, places, roles...) described from
 * several documents are emitted once, as the {@code RdfConverter} contract requires.
 */
final class RdfSink {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    private final RDFHandler handler;
    private final Set<Statement> sent = new HashSet<>();

    RdfSink(RDFHandler handler) {
        this.handler = handler;
    }

    void add(Resource subject, IRI predicate, Value object) {
        Statement st = VF.createStatement(subject, predicate, object);
        if (sent.add(st)) {
            handler.handleStatement(st);
        }
    }

    void type(Resource subject, IRI type) {
        add(subject, RDF.TYPE, type);
    }

    /** Adds a plain literal; blank values are ignored. */
    void literal(Resource subject, IRI predicate, String value) {
        if (value != null && !value.isBlank()) {
            add(subject, predicate, VF.createLiteral(value.strip()));
        }
    }

    /** Adds an {@code xsd:date} literal when {@code value} is an ISO date, else a plain literal. */
    void date(Resource subject, IRI predicate, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.strip();
        if (isIsoDate(v)) {
            add(subject, predicate, VF.createLiteral(v, XSD.DATE));
        } else {
            literal(subject, predicate, v);
        }
    }

    /** Adds an {@code xsd:gYear} literal when {@code value} is a four-digit year, else a plain literal. */
    void year(Resource subject, IRI predicate, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.strip();
        if (YEAR.matcher(v).matches()) {
            add(subject, predicate, VF.createLiteral(v, XSD.GYEAR));
        } else {
            literal(subject, predicate, v);
        }
    }

    static boolean isIsoDate(String v) {
        if (!ISO_DATE.matcher(v).matches()) {
            return false;
        }
        try {
            LocalDate.parse(v);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}

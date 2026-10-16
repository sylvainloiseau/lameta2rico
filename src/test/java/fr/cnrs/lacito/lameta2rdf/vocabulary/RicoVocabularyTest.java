package fr.cnrs.lacito.lameta2rdf.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RicoVocabularyTest {

    static Model ontology;

    @BeforeAll
    static void loadOntology() throws IOException {
        ontology = loadRicO();
    }

    /** The bundled RiC-O 1.0.2 ontology. */
    public static Model loadRicO() throws IOException {
        try (InputStream in = RicoVocabularyTest.class.getResourceAsStream("/ontologies/RiC-O_1-0-2.rdf")) {
            return Rio.parse(in, "https://www.ica.org/standards/RiC/ontology", RDFFormat.RDFXML);
        }
    }

    static List<IRI> ricoConstants() throws IllegalAccessException {
        List<IRI> terms = new ArrayList<>();
        for (Field f : RICO.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == IRI.class) {
                terms.add((IRI) f.get(null));
            }
        }
        return terms;
    }

    @Test
    void everyConstantIsDeclaredInRicO() throws IllegalAccessException {
        Set<IRI> declarationTypes = Set.of(OWL.CLASS, OWL.OBJECTPROPERTY, OWL.DATATYPEPROPERTY);
        List<IRI> terms = ricoConstants();
        assertThat(terms).hasSizeGreaterThan(50);
        for (IRI term : terms) {
            assertThat(ontology.filter(term, RDF.TYPE, null).objects())
                    .as("%s is declared in RiC-O 1.0.2", term)
                    .anyMatch(declarationTypes::contains);
        }
    }

    @Test
    void namespaceIsTheOntologyNamespace() {
        assertThat(RICO.NAMESPACE).isEqualTo("https://www.ica.org/standards/RiC/ontology#");
        assertThat(ontology.getNamespace("rico")).hasValueSatisfying(ns -> assertThat(ns.getName()).isEqualTo(RICO.NAMESPACE));
    }
}

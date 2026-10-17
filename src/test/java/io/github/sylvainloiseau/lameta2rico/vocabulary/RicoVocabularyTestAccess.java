package io.github.sylvainloiseau.lameta2rico.vocabulary;

import java.io.IOException;

import org.eclipse.rdf4j.model.Model;

/** Gives tests in other packages access to the bundled ontology loader. */
public final class RicoVocabularyTestAccess {

    private RicoVocabularyTestAccess() {
    }

    public static Model ricO() throws IOException {
        return RicoVocabularyTest.loadRicO();
    }
}

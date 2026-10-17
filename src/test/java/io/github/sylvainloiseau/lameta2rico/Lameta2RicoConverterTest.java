package io.github.sylvainloiseau.lameta2rico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicRst;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicoVocabularyTestAccess;
import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;
import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;
import fr.cnrs.lacito.ricimporterplugin.RdfConverter;
import fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider;
import fr.cnrs.lacito.ricimporterplugin.RdfConverters;
import fr.cnrs.lacito.ricimporterplugin.RepositoryLoader;

class Lameta2RicoConverterTest {

    @Test
    void testProjectMatchesGoldenFile() {
        Model actual = TestProjects.convert("Test");
        Model expected = TestProjects.expectedTestModel();
        assertThat(Models.isomorphic(actual, expected))
                .as("output differs from src/test/resources/expected/Test.ttl")
                .isTrue();
    }

    @Test
    void test2ConvertsDespiteMismatchedAndEmptyFiles() {
        Model m = TestProjects.convert("Test2");
        assertThat(m).hasSizeGreaterThan(300);
        assertThat(m.contains(TestProjects.iri("/session/Session3"), RICO.identifier, null)).isTrue();
    }

    @Test
    void onlyKnownVocabulariesAreUsed() throws IOException {
        Model ontology = RicoVocabularyTestAccess.ricO();
        Set<String> allowedNamespaces = Set.of(RDF.NAMESPACE, RDFS.NAMESPACE, XSD.NAMESPACE, SKOS.NAMESPACE);
        Model m = TestProjects.convert("Test");
        Set<IRI> terms = new HashSet<>(m.predicates());
        m.filter(null, RDF.TYPE, null).objects().forEach(o -> terms.add((IRI) o));
        for (IRI term : terms) {
            if (term.getNamespace().equals(RICO.NAMESPACE)) {
                assertThat(ontology.contains(term, RDF.TYPE, null)).as("%s is in RiC-O 1.0.2", term).isTrue();
            } else {
                assertThat(allowedNamespaces).as("namespace of %s", term).contains(term.getNamespace());
            }
        }
        assertThat(m.filter(null, RICO.hasRecordSetType, null).objects())
                .allMatch(o -> o.stringValue().startsWith(RicRst.NAMESPACE));
        assertThat(m.objects().stream().map(Value::stringValue)).noneMatch(s -> s.startsWith("https://github.com/onset/lameta/"));
    }

    @Test
    void streamingEmitsNoDuplicatesAndEqualsToModel() {
        List<Statement> streamed = new ArrayList<>();
        int[] lifecycle = new int[2];
        TestProjects.converter(TestProjects.project("Test")).convert(new AbstractRDFHandler() {
            @Override
            public void startRDF() {
                lifecycle[0]++;
            }

            @Override
            public void endRDF() {
                lifecycle[1]++;
            }

            @Override
            public void handleStatement(Statement st) {
                streamed.add(st);
            }
        });
        assertThat(lifecycle).containsExactly(1, 1);
        assertThat(new HashSet<>(streamed)).hasSameSizeAs(streamed);
        assertThat(streamed).allMatch(st -> st.getContext() == null);
        assertThat(Models.isomorphic(streamed, TestProjects.convert("Test"))).isTrue();
    }

    @Test
    void repeatedRunsGiveTheSameGraph() {
        RdfConverter c = TestProjects.converter(TestProjects.project("Test"));
        assertThat(Models.isomorphic(c.toModel(), c.toModel())).isTrue();
    }

    @Test
    void suggestedContextIsTheProject() {
        assertThat(TestProjects.converter(TestProjects.project("Test")).suggestedContext())
                .contains(TestProjects.iri("/project/Test"));
    }

    @Test
    void invalidInputAndOptionsAreRejectedEagerly(@TempDir Path tmp) {
        assertThatThrownBy(() -> TestProjects.converter(tmp)).isInstanceOf(RdfConversionException.class);
        assertThatThrownBy(() -> new Lameta2RicoConverter(TestProjects.project("Test"),
                ConverterOptions.builder(TestProjects.BASE).set("unmapped", "keep").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerIsDiscoveredThroughServiceLoader() {
        RdfConverterProvider provider = RdfConverters.get("lameta");
        assertThat(provider).isInstanceOf(Lameta2RicoConverterProvider.class);
        assertThat(provider.options()).extracting(o -> o.name()).containsExactly("includeContactInfo", "unmapped");
        RdfConverter c = provider.create(TestProjects.project("Test"), ConverterOptions.of(TestProjects.BASE));
        assertThat(Models.isomorphic(c.toModel(), TestProjects.expectedTestModel())).isTrue();
    }

    @Test
    void loadsIntoRepositoryProjectGraph() {
        Repository repo = new SailRepository(new MemoryStore());
        try {
            RdfConverter c = TestProjects.converter(TestProjects.project("Test"));
            long n = RepositoryLoader.replaceGraph(repo, c);
            RepositoryLoader.replaceGraph(repo, c);
            IRI graph = c.suggestedContext().orElseThrow();
            try (RepositoryConnection conn = repo.getConnection()) {
                assertThat(conn.size(graph)).isEqualTo(n);
                assertThat(conn.size()).isEqualTo(n);
                List<Statement> stored = conn.getStatements(null, null, null, graph).stream().toList();
                Model expected = c.toModel();
                assertThat(Models.isomorphic(
                        stored.stream().map(st -> org.eclipse.rdf4j.model.util.Statements.statement(
                                st.getSubject(), st.getPredicate(), st.getObject(), null)).toList(),
                        expected)).isTrue();
                assertThat(conn.getNamespace("rico")).isEqualTo(RICO.NAMESPACE);
            }
        } finally {
            repo.shutDown();
        }
    }
}

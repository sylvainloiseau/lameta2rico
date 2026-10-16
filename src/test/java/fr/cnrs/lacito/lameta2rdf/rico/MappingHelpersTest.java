package fr.cnrs.lacito.lameta2rdf.rico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.jupiter.api.Test;

import fr.cnrs.lacito.lameta2rdf.vocabulary.RICO;

class MappingHelpersTest {

    @Test
    void fileTypeFromLametaNamingConvention() {
        assertThat(DocumentMapper.fileType("John Doe_Consent.txt", "John Doe")).contains("Consent");
        assertThat(DocumentMapper.fileType("S1_Careful_Speech.wav", "S1")).contains("Careful_Speech");
        assertThat(DocumentMapper.fileType("131123_03_Source.WAV", "Session1_ID")).contains("Source");
        assertThat(DocumentMapper.fileType("x_Careful_Speech.wav", "other")).contains("Careful_Speech");
        assertThat(DocumentMapper.fileType("131109_03.WAV", "Session1")).isEmpty();
        assertThat(DocumentMapper.fileType("idees.txt", "DescriptionDocuments")).isEmpty();
    }

    @Test
    void humanizedLabels() {
        assertThat(UnmappedFieldHandler.humanize("primaryLanguageLearnedIn")).isEqualTo("Primary language learned in");
        assertThat(UnmappedFieldHandler.humanize("fathersLanguage")).isEqualTo("Fathers language");
        assertThat(UnmappedFieldHandler.humanize("Social_Context")).isEqualTo("Social context");
        assertThat(UnmappedFieldHandler.humanize("education")).isEqualTo("Education");
    }

    @Test
    void listSplitting() {
        assertThat(SessionMapper.splitList("JD;JD;", ";")).containsExactly("JD");
        assertThat(SessionMapper.splitList("Keyword1, keyword2", ",")).containsExactly("Keyword1", "keyword2");
        assertThat(SessionMapper.splitList(" ; ", ";")).isEmpty();
    }

    @Test
    void isoDates() {
        assertThat(RdfSink.isIsoDate("2021-09-09")).isTrue();
        assertThat(RdfSink.isIsoDate("2021-02-30")).isFalse();
        assertThat(RdfSink.isIsoDate("09/09/2021")).isFalse();
    }

    @Test
    void uriSegmentsArePercentEncoded() {
        UriFactory uris = new UriFactory("http://ex.org/c", "My Project");
        assertThat(uris.project().stringValue()).isEqualTo("http://ex.org/c/project/My%20Project");
        assertThat(uris.vocabulary("place", null, "emplacement/Adresse").stringValue())
                .isEqualTo("http://ex.org/c/place/emplacement%2FAdresse");
        assertThat(uris.agent("Déposant").stringValue()).isEqualTo("http://ex.org/c/agent/D%C3%A9posant");
    }

    @Test
    void languageRegistryReconcilesCodesAndNames() {
        Model m = new LinkedHashModel();
        StatementCollector collector = new StatementCollector(m);
        VocabularyRegistry v = new VocabularyRegistry(new UriFactory("http://ex.org/c", "P"), new RdfSink(collector));
        v.registerCodeAndName("tww : Tuwari");
        assertThat(v.language("Tuwari")).isEqualTo(Values.iri("http://ex.org/c/language/tww"));
        assertThat(v.language("tuwari")).isEqualTo(Values.iri("http://ex.org/c/language/tww"));
        assertThat(v.language("tpi")).isEqualTo(Values.iri("http://ex.org/c/language/tpi"));
        assertThat(v.language("Tok Pisin")).isEqualTo(Values.iri("http://ex.org/c/language/name/Tok%20Pisin"));
        assertThat(m.filter(Values.iri("http://ex.org/c/language/tww"), RICO.name, null).objects())
                .extracting(o -> o.stringValue()).containsExactly("Tuwari");
    }

    @Test
    void unmappedPolicyParsing() {
        assertThat(UnmappedPolicy.parse("DROP")).isEqualTo(UnmappedPolicy.DROP);
        assertThat(UnmappedPolicy.parse(" description ")).isEqualTo(UnmappedPolicy.DESCRIPTION);
        assertThatThrownBy(() -> UnmappedPolicy.parse("keep")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinkDropsRepeatedStatements() {
        Model m = new LinkedHashModel();
        int[] count = {0};
        RdfSink sink = new RdfSink(new StatementCollector(m) {
            @Override
            public void handleStatement(org.eclipse.rdf4j.model.Statement st) {
                count[0]++;
                super.handleStatement(st);
            }
        });
        var s = Values.iri("http://ex.org/s");
        sink.literal(s, RICO.name, "a");
        sink.literal(s, RICO.name, "a");
        sink.literal(s, RICO.name, "  ");
        assertThat(count[0]).isEqualTo(1);
    }
}

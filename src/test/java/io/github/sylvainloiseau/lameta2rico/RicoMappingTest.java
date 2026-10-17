package io.github.sylvainloiseau.lameta2rico;

import static io.github.sylvainloiseau.lameta2rico.TestProjects.iri;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicRst;
import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;

/** Checks the mapping rules of plan §5 on the converted {@code Test} sample project. */
class RicoMappingTest {

    static Model model;

    static final IRI PROJECT = iri("/project/Test");
    static final IRI JD = iri("/person/JD");
    static final IRI TWW = iri("/language/tww");
    static final IRI SESSION1 = iri("/session/Session1_ID");
    static final IRI ACTIVITY1 = iri("/session/Session1_ID/activity");
    static final IRI WAV_RECORD = iri("/session/Session1_ID/record/131123_03_Source.WAV");
    static final IRI WAV_INSTANTIATION = iri("/session/Session1_ID/instantiation/131123_03_Source.WAV");

    @BeforeAll
    static void convert() {
        model = TestProjects.convert("Test");
    }

    static Set<Value> objects(Resource s, IRI p) {
        return model.filter(s, p, null).objects();
    }

    static Set<String> strings(Resource s, IRI p) {
        return model.filter(s, p, null).objects().stream().map(Value::stringValue)
                .collect(java.util.stream.Collectors.toSet());
    }

    static boolean has(Resource s, IRI p, Value o) {
        return model.contains(s, p, o);
    }

    @Nested
    class Project {
        @Test
        void recordSetFonds() {
            assertThat(has(PROJECT, RDF.TYPE, RICO.RecordSet)).isTrue();
            assertThat(has(PROJECT, RICO.hasRecordSetType, RicRst.Fonds)).isTrue();
            assertThat(strings(PROJECT, RICO.title)).containsExactly("Test");
            assertThat(strings(PROJECT, RICO.scopeAndContent)).containsExactly("Description");
        }

        @Test
        void languagesSplitIntoCodeAndName() {
            assertThat(objects(PROJECT, RICO.hasOrHadSomeMembersWithLanguage)).containsExactlyInAnyOrder(TWW, iri("/language/en"));
            assertThat(has(TWW, RDF.TYPE, RICO.Language)).isTrue();
            assertThat(strings(TWW, RICO.identifier)).containsExactly("tww");
            assertThat(strings(TWW, RICO.name)).containsExactly("Tuwari");
        }

        @Test
        void placeChain() {
            IRI location = iri("/place/emplacement%2FAdresse");
            assertThat(objects(PROJECT, RICO.isAssociatedWithPlace)).containsExactly(location);
            assertThat(strings(location, RICO.location)).containsExactly("emplacement/Adresse");
            assertThat(objects(location, RICO.isOrWasPartOf)).containsExactly(iri("/place/Region"));
            assertThat(objects(iri("/place/Region"), RICO.isOrWasPartOf)).containsExactly(iri("/place/Papua%20New%20Guinea"));
            assertThat(objects(iri("/place/Papua%20New%20Guinea"), RICO.isOrWasPartOf)).containsExactly(iri("/place/Oceania"));
        }

        @Test
        void fundingAccessAgentsAndDate() {
            IRI funding = iri("/activity/funding/GrandID");
            assertThat(objects(PROJECT, RICO.resultsOrResultedFrom)).containsExactly(funding);
            assertThat(strings(funding, RICO.name)).containsExactly("FundingProjectTitle");
            assertThat(objects(PROJECT, RICO.isOrWasRegulatedBy)).containsExactly(iri("/rule/accessProtocol/PARADISEC"));
            IRI depositor = iri("/agent/D%C3%A9posant");
            assertThat(objects(PROJECT, RICO.hasAccumulator)).containsExactly(depositor);
            assertThat(has(depositor, RDF.TYPE, RICO.Agent)).isTrue();
            assertThat(objects(PROJECT, RICO.hasOrHadManager)).containsExactly(iri("/agent/contact%20Person"));
            assertThat(objects(PROJECT, RICO.publicationDate)).singleElement()
                    .satisfies(v -> assertThat(((Literal) v).getDatatype()).isEqualTo(XSD.DATE));
        }

        @Test
        void folderRecordSets() {
            assertThat(objects(PROJECT, RICO.includesOrIncluded)).containsExactlyInAnyOrder(
                    iri("/project/Test/Sessions"), iri("/project/Test/People"),
                    iri("/project/Test/DescriptionDocuments"), iri("/project/Test/OtherDocuments"));
            assertThat(has(iri("/project/Test/Sessions"), RICO.hasRecordSetType, RicRst.Series)).isTrue();
        }
    }

    @Nested
    class Person {
        @Test
        void identityAndNames() {
            assertThat(has(JD, RDF.TYPE, RICO.Person)).isTrue();
            assertThat(strings(JD, RICO.name)).containsExactly("John Doe");
            assertThat(strings(JD, RICO.identifier)).containsExactly("JD");
            assertThat(strings(iri("/person/JD/name/nickname"), RICO.textualValue)).containsExactly("John");
        }

        @Test
        void languageGivenByNameResolvesToCodedNode() {
            assertThat(objects(JD, RICO.hasOrHadLanguage))
                    .containsExactlyInAnyOrder(TWW, iri("/language/tpi"), iri("/language/en"));
        }

        @Test
        void birthYearGenderOccupation() {
            assertThat(objects(JD, RICO.birthDate)).singleElement()
                    .satisfies(v -> assertThat(((Literal) v).getDatatype()).isEqualTo(XSD.GYEAR));
            assertThat(objects(JD, RICO.hasOrHadDemographicGroup)).contains(iri("/demographicGroup/gender/Male"));
            assertThat(objects(JD, RICO.hasOrHadOccupationOfType)).containsExactly(iri("/occupationType/PrimaryOccupation"));
        }

        @Test
        void notesDescriptionAndUnmappedFields() {
            assertThat(strings(JD, SKOS.NOTE)).containsExactly("Notes");
            assertThat(strings(JD, RICO.generalDescription)).contains("Description", "Fathers language: en",
                    "CustomField1: CustomField1Value", "Education: Formation");
        }

        @Test
        void contactInfoIsNotEmittedByDefault() {
            assertThat(model.objects().stream().map(Value::stringValue)).noneMatch(s -> s.contains("HowToContact"));
        }

        @Test
        void documentsRecordSet() {
            IRI docs = iri("/person/John%20Doe/documents");
            assertThat(objects(docs, RICO.hasOrHadMainSubject)).containsExactly(JD);
            assertThat(has(iri("/project/Test/People"), RICO.includesOrIncluded, docs)).isTrue();
            IRI consent = iri("/person/John%20Doe/documents/record/John%20Doe_Consent.txt");
            assertThat(objects(consent, RICO.hasDocumentaryFormType)).containsExactly(iri("/documentaryFormType/Consent"));
            assertThat(objects(consent, RICO.hasCreator)).as("author role").containsExactly(JD);
        }
    }

    @Nested
    class Session {
        @Test
        void recordSetDocumentsActivity() {
            assertThat(has(SESSION1, RICO.hasRecordSetType, RicRst.File)).isTrue();
            assertThat(objects(SESSION1, RICO.documents)).containsExactly(ACTIVITY1);
            assertThat(has(ACTIVITY1, RDF.TYPE, RICO.Activity)).isTrue();
            assertThat(strings(SESSION1, RICO.title)).containsExactly("Session 1 Title");
            assertThat(strings(ACTIVITY1, RICO.name)).containsExactly("Session 1 Title");
        }

        @Test
        void participantsAreDeduplicatedAndResolved() {
            assertThat(objects(ACTIVITY1, RICO.hasOrHadParticipant)).containsExactly(JD);
        }

        @Test
        void activityTypes() {
            assertThat(objects(ACTIVITY1, RICO.hasActivityType)).containsExactlyInAnyOrder(
                    iri("/activityType/genre/singing"), iri("/activityType/subGenre/Chant%20solo"),
                    iri("/activityType/involvement/elicited"), iri("/activityType/planningType/semi-spontaneous"),
                    iri("/activityType/socialContext/family"));
            assertThat(objects(iri("/activityType/subGenre/Chant%20solo"), SKOS.BROADER))
                    .containsExactly(iri("/activityType/genre/singing"));
        }

        @Test
        void accessStatusDateSubjects() {
            IRI access = iri("/rule/access/O");
            assertThat(objects(SESSION1, RICO.isOrWasRegulatedBy)).containsExactly(access);
            assertThat(objects(access, RICO.isOrWasPartOf)).containsExactly(iri("/rule/accessProtocol/PARADISEC"));
            assertThat(strings(SESSION1, RICO.conditionsOfAccess)).containsExactly("open");
            assertThat(objects(SESSION1, RICO.hasOrHadAllMembersWithRecordState)).containsExactly(iri("/recordState/In_Progress"));
            assertThat(objects(ACTIVITY1, RICO.date)).singleElement()
                    .satisfies(v -> assertThat(((Literal) v).getDatatype()).isEqualTo(XSD.DATE));
            assertThat(objects(SESSION1, RICO.hasOrHadSubject)).containsExactlyInAnyOrder(
                    iri("/concept/keyword/Keyword1"), iri("/concept/keyword/keyword2"));
            assertThat(objects(SESSION1, RICO.hasOrHadMainSubject)).containsExactly(iri("/concept/topic/Topic"));
        }

        @Test
        void locationChainOnActivity() {
            assertThat(objects(ACTIVITY1, RICO.hasOrHadLocation)).containsExactly(iri("/place/emplacement"));
            assertThat(objects(iri("/place/emplacement"), RICO.isOrWasPartOf)).containsExactly(iri("/place/oceania"));
        }

        @Test
        void contributionsBecomeCreationRelations() {
            IRI c1 = iri("/session/Session1_ID/creation/1");
            assertThat(has(c1, RDF.TYPE, RICO.CreationRelation)).isTrue();
            assertThat(objects(c1, RICO.relationHasSource)).containsExactly(SESSION1);
            assertThat(objects(c1, RICO.relationHasTarget)).containsExactly(JD);
            assertThat(objects(c1, RICO.creationWithRole)).containsExactly(iri("/roleType/participant"));
            assertThat(strings(c1, RICO.generalDescription)).containsExactly("Com");
            assertThat(objects(c1, RICO.date)).as("0001-01-01 means no date").isEmpty();
            assertThat(objects(SESSION1, RICO.hasCreator)).as("participant is not a creator").isEmpty();
        }
    }

    @Nested
    class Document {
        @Test
        void recordAndInstantiation() {
            assertThat(has(SESSION1, RICO.includesOrIncluded, WAV_RECORD)).isTrue();
            assertThat(has(WAV_RECORD, RDF.TYPE, RICO.Record)).isTrue();
            assertThat(objects(WAV_RECORD, RICO.hasOrHadDigitalInstantiation)).containsExactly(WAV_INSTANTIATION);
            assertThat(has(WAV_INSTANTIATION, RDF.TYPE, RICO.Instantiation)).isTrue();
            assertThat(objects(WAV_INSTANTIATION, RICO.isOrWasDigitalInstantiationOf)).containsExactly(WAV_RECORD);
            assertThat(strings(WAV_INSTANTIATION, RICO.identifier)).containsExactly("Sessions/Session1_ID/131123_03_Source.WAV");
        }

        @Test
        void fileTypeAndSessionLanguages() {
            assertThat(objects(WAV_RECORD, RICO.hasDocumentaryFormType)).containsExactly(iri("/documentaryFormType/Source"));
            assertThat(objects(WAV_RECORD, RICO.hasOrHadLanguage)).containsExactlyInAnyOrder(TWW, iri("/language/en"));
        }

        @Test
        void descriptionDocumentsDescribeTheProject() {
            assertThat(objects(iri("/project/Test/DescriptionDocuments/record/idees.txt"), RICO.describesOrDescribed))
                    .containsExactly(PROJECT);
            assertThat(objects(iri("/project/Test/OtherDocuments/record/idees.txt"), RICO.describesOrDescribed)).isEmpty();
        }
    }

    @Nested
    class Options {
        @Test
        void includeContactInfo() {
            Model m = new Lameta2RicoConverter(TestProjects.project("Test"),
                    ConverterOptions.builder(TestProjects.BASE).set("includeContactInfo", true).build()).toModel();
            assertThat(m.filter(JD, RICO.generalDescription, null).objects())
                    .extracting(Value::stringValue).contains("How to contact: HowToContact");
        }

        @Test
        void dropUnmapped() {
            Model m = new Lameta2RicoConverter(TestProjects.project("Test"),
                    ConverterOptions.builder(TestProjects.BASE).set("unmapped", "drop").build()).toModel();
            assertThat(m.filter(JD, RICO.generalDescription, null).objects())
                    .extracting(Value::stringValue).containsExactly("Description");
        }
    }
}

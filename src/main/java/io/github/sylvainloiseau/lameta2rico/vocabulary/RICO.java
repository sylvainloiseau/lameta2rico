package io.github.sylvainloiseau.lameta2rico.vocabulary;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;

/**
 * Terms of the RiC-O 1.0.2 ontology (Records in Contexts) used by the converter.
 * Every constant is checked against {@code ontologies/RiC-O_1-0-2.rdf} by the tests.
 */
public final class RICO {

    public static final String NAMESPACE = "https://www.ica.org/standards/RiC/ontology#";
    public static final String PREFIX = "rico";
    public static final Namespace NS = new SimpleNamespace(PREFIX, NAMESPACE);

    // Classes
    public static final IRI Activity = term("Activity");
    public static final IRI ActivityType = term("ActivityType");
    public static final IRI Agent = term("Agent");
    public static final IRI AgentName = term("AgentName");
    public static final IRI Concept = term("Concept");
    public static final IRI CreationRelation = term("CreationRelation");
    public static final IRI DemographicGroup = term("DemographicGroup");
    public static final IRI DocumentaryFormType = term("DocumentaryFormType");
    public static final IRI Instantiation = term("Instantiation");
    public static final IRI Language = term("Language");
    public static final IRI OccupationType = term("OccupationType");
    public static final IRI Person = term("Person");
    public static final IRI Place = term("Place");
    public static final IRI Record = term("Record");
    public static final IRI RecordSet = term("RecordSet");
    public static final IRI RecordState = term("RecordState");
    public static final IRI RoleType = term("RoleType");
    public static final IRI Rule = term("Rule");

    // Object properties
    public static final IRI creationWithRole = term("creationWithRole");
    public static final IRI describesOrDescribed = term("describesOrDescribed");
    public static final IRI documents = term("documents");
    public static final IRI hasAccumulator = term("hasAccumulator");
    public static final IRI hasActivityType = term("hasActivityType");
    public static final IRI hasCreator = term("hasCreator");
    public static final IRI hasDocumentaryFormType = term("hasDocumentaryFormType");
    public static final IRI hasOrHadAgentName = term("hasOrHadAgentName");
    public static final IRI hasOrHadAllMembersWithRecordState = term("hasOrHadAllMembersWithRecordState");
    public static final IRI hasOrHadDemographicGroup = term("hasOrHadDemographicGroup");
    public static final IRI hasOrHadDigitalInstantiation = term("hasOrHadDigitalInstantiation");
    public static final IRI hasOrHadLanguage = term("hasOrHadLanguage");
    public static final IRI hasOrHadLocation = term("hasOrHadLocation");
    public static final IRI hasOrHadMainSubject = term("hasOrHadMainSubject");
    public static final IRI hasOrHadManager = term("hasOrHadManager");
    public static final IRI hasOrHadOccupationOfType = term("hasOrHadOccupationOfType");
    public static final IRI hasOrHadParticipant = term("hasOrHadParticipant");
    public static final IRI hasOrHadSomeMembersWithLanguage = term("hasOrHadSomeMembersWithLanguage");
    public static final IRI hasOrHadSubject = term("hasOrHadSubject");
    public static final IRI hasRecordSetType = term("hasRecordSetType");
    public static final IRI includesOrIncluded = term("includesOrIncluded");
    public static final IRI isAssociatedWithPlace = term("isAssociatedWithPlace");
    public static final IRI isOrWasDigitalInstantiationOf = term("isOrWasDigitalInstantiationOf");
    public static final IRI isOrWasPartOf = term("isOrWasPartOf");
    public static final IRI isOrWasRegulatedBy = term("isOrWasRegulatedBy");
    public static final IRI relationHasSource = term("relationHasSource");
    public static final IRI relationHasTarget = term("relationHasTarget");
    public static final IRI resultsOrResultedFrom = term("resultsOrResultedFrom");

    // Datatype properties
    public static final IRI birthDate = term("birthDate");
    public static final IRI conditionsOfAccess = term("conditionsOfAccess");
    public static final IRI creationDate = term("creationDate");
    public static final IRI date = term("date");
    public static final IRI generalDescription = term("generalDescription");
    public static final IRI identifier = term("identifier");
    public static final IRI location = term("location");
    public static final IRI name = term("name");
    public static final IRI publicationDate = term("publicationDate");
    public static final IRI scopeAndContent = term("scopeAndContent");
    public static final IRI textualValue = term("textualValue");
    public static final IRI title = term("title");

    private RICO() {
    }

    private static IRI term(String localName) {
        return Values.iri(NAMESPACE, localName);
    }
}

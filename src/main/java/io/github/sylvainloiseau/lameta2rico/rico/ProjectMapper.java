package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.List;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;

import io.github.sylvainloiseau.lameta2rico.lameta.LametaLayout;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Contribution;
import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.model.LametaProject;
import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicRst;

/** The {@code .sprj} file and the project-level record sets (plan §5.1, §5.3). */
final class ProjectMapper {

    static final String VERNACULAR_LANGUAGE = "VernacularISO3CodeAndName";
    static final String ANALYSIS_LANGUAGE = "AnalysisISO3CodeAndName";

    private final ConversionContext ctx;
    private final DocumentMapper documents;
    private final ContributionMapper contributions;

    ProjectMapper(ConversionContext ctx, DocumentMapper documents) {
        this.ctx = ctx;
        this.documents = documents;
        this.contributions = new ContributionMapper(ctx);
    }

    /**
     * Registers the project languages' code ↔ name pairs. Runs before anything else, so that
     * people's languages given by name resolve to the coded nodes.
     */
    void registerLanguages(XmlElement metadata) {
        metadata.childText(VERNACULAR_LANGUAGE).ifPresent(ctx.vocabulary::registerCodeAndName);
        metadata.childText(ANALYSIS_LANGUAGE).ifPresent(ctx.vocabulary::registerCodeAndName);
    }

    void map(LametaProject project) {
        IRI pr = ctx.uris.project();
        ctx.sink.type(pr, RICO.RecordSet);
        ctx.sink.add(pr, RICO.hasRecordSetType, RicRst.Fonds);

        FieldCursor f = new FieldCursor(project.metadata());
        f.text("Title").ifPresent(v -> ctx.sink.literal(pr, RICO.title, v));
        f.text("ProjectDescription").ifPresent(v -> ctx.sink.literal(pr, RICO.scopeAndContent, v));
        for (String field : List.of(VERNACULAR_LANGUAGE, ANALYSIS_LANGUAGE)) {
            f.text(field).flatMap(ctx.vocabulary::registerCodeAndName)
                    .ifPresent(l -> ctx.sink.add(pr, RICO.hasOrHadSomeMembersWithLanguage, l));
        }
        ctx.vocabulary.placeChain(true,
                        f.text("Location").orElse(null), f.text("Region").orElse(null),
                        f.text("Country").orElse(null), f.text("Continent").orElse(null))
                .ifPresent(place -> ctx.sink.add(pr, RICO.isAssociatedWithPlace, place));

        Optional<String> grantId = f.text("grantId");
        Optional<String> fundingTitle = f.text("FundingProjectTitle");
        grantId.or(() -> fundingTitle).ifPresent(key -> {
            IRI funding = ctx.uris.vocabulary("activity", "funding", key);
            ctx.sink.add(pr, RICO.resultsOrResultedFrom, funding);
            ctx.sink.type(funding, RICO.Activity);
            fundingTitle.ifPresent(t -> ctx.sink.literal(funding, RICO.name, t));
            grantId.ifPresent(id -> ctx.sink.literal(funding, RICO.identifier, id));
        });

        f.text("AccessProtocol").ifPresent(v -> {
            IRI rule = ctx.vocabulary.term("rule", "accessProtocol", v, RICO.Rule);
            ctx.sink.add(pr, RICO.isOrWasRegulatedBy, rule);
            ctx.accessProtocol = Optional.of(rule);
        });
        f.text("Depositor").ifPresent(v -> ctx.sink.add(pr, RICO.hasAccumulator, ctx.agents.resolveAgent(v)));
        f.text("ContactPerson").ifPresent(v -> ctx.sink.add(pr, RICO.hasOrHadManager, ctx.agents.resolveAgent(v)));
        f.text("DateAvailable").ifPresent(v -> ctx.sink.date(pr, RICO.publicationDate, v));
        f.consume("contributions");
        contributions.map(pr, Contribution.from(project.metadata()), Optional.empty());
        f.child("CustomFields").ifPresent(cf -> ctx.unmapped.handleCustomFields(pr, cf));
        ctx.unmapped.handle(pr, f.remaining());

        folder(LametaLayout.SESSIONS_DIR);
        folder(LametaLayout.PEOPLE_DIR);
        project.descriptionDocuments().ifPresent(files -> documentFolder(LametaLayout.DESCRIPTION_DOCUMENTS_DIR, files, Optional.of(pr)));
        project.otherDocuments().ifPresent(files -> documentFolder(LametaLayout.OTHER_DOCUMENTS_DIR, files, Optional.empty()));
    }

    private IRI folder(String name) {
        IRI set = ctx.uris.projectFolder(name);
        ctx.sink.add(ctx.uris.project(), RICO.includesOrIncluded, set);
        ctx.sink.type(set, RICO.RecordSet);
        ctx.sink.add(set, RICO.hasRecordSetType, RicRst.Series);
        ctx.sink.literal(set, RICO.title, name);
        return set;
    }

    private void documentFolder(String name, List<DocumentFile> files, Optional<IRI> describes) {
        IRI set = folder(name);
        for (DocumentFile d : files) {
            documents.map(d, set, name, List.of(), describes);
        }
    }
}

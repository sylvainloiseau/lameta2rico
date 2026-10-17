package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.SKOS;

import io.github.sylvainloiseau.lameta2rico.lameta.LametaLayout;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Contribution;
import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Folder;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicRst;

/**
 * A {@code People/<name>} folder → a {@code rico:Person} and a {@code rico:RecordSet} holding
 * that person's documents (plan §5.1, §5.5).
 */
final class PersonMapper {

    private static final Pattern OTHER_LANGUAGE = Pattern.compile("otherLanguage\\d+");

    private final ConversionContext ctx;
    private final DocumentMapper documents;
    private final ContributionMapper contributions;

    PersonMapper(ConversionContext ctx, DocumentMapper documents) {
        this.ctx = ctx;
        this.documents = documents;
        this.contributions = new ContributionMapper(ctx);
    }

    void map(Folder folder) {
        IRI person = ctx.agents.iriOf(folder);
        IRI documentSet = ctx.uris.personDocuments(folder.dirName());

        ctx.sink.add(ctx.uris.projectFolder(LametaLayout.PEOPLE_DIR), RICO.includesOrIncluded, documentSet);
        ctx.sink.type(documentSet, RICO.RecordSet);
        ctx.sink.add(documentSet, RICO.hasRecordSetType, RicRst.File);
        ctx.sink.literal(documentSet, RICO.title, folder.dirName());
        ctx.sink.add(documentSet, RICO.hasOrHadMainSubject, person);

        ctx.sink.type(person, RICO.Person);
        FieldCursor f = new FieldCursor(folder.metadata());
        f.text("name").ifPresent(v -> ctx.sink.literal(person, RICO.name, v));
        f.text("nickName").ifPresent(v -> {
            IRI nickname = ctx.uris.nickname(person);
            ctx.sink.add(person, RICO.hasOrHadAgentName, nickname);
            ctx.sink.type(nickname, RICO.AgentName);
            ctx.sink.literal(nickname, RICO.textualValue, v);
        });
        f.text("code").ifPresent(v -> ctx.sink.literal(person, RICO.identifier, v));
        f.text("primaryLanguage").ifPresent(v -> ctx.sink.add(person, RICO.hasOrHadLanguage, ctx.vocabulary.language(v)));
        f.texts(n -> OTHER_LANGUAGE.matcher(n).matches())
                .forEach(v -> ctx.sink.add(person, RICO.hasOrHadLanguage, ctx.vocabulary.language(v)));
        f.text("birthYear").ifPresent(v -> ctx.sink.year(person, RICO.birthDate, v));
        f.text("gender").ifPresent(v -> ctx.sink.add(person, RICO.hasOrHadDemographicGroup,
                ctx.vocabulary.term("demographicGroup", "gender", v, RICO.DemographicGroup)));
        f.text("ethnicGroup").ifPresent(v -> ctx.sink.add(person, RICO.hasOrHadDemographicGroup,
                ctx.vocabulary.term("demographicGroup", "ethnicGroup", v, RICO.DemographicGroup)));
        f.text("primaryOccupation").ifPresent(v -> ctx.sink.add(person, RICO.hasOrHadOccupationOfType,
                ctx.vocabulary.term("occupationType", null, v, RICO.OccupationType)));
        f.text("description").ifPresent(v -> ctx.sink.literal(person, RICO.generalDescription, v));
        f.text("notes").ifPresent(v -> ctx.sink.literal(person, SKOS.NOTE, v));
        f.child("howToContact")
                .filter(c -> ctx.includeContactInfo)
                .ifPresent(c -> ctx.unmapped.handle(person, List.of(c)));
        f.consume("contributions");
        contributions.map(documentSet, Contribution.from(folder.metadata()), Optional.empty());
        f.child("CustomFields").ifPresent(cf -> ctx.unmapped.handleCustomFields(person, cf));
        ctx.unmapped.handle(person, f.remaining());

        for (DocumentFile d : folder.files()) {
            documents.map(d, documentSet, folder.dirName(), List.of(), Optional.empty());
        }
    }
}

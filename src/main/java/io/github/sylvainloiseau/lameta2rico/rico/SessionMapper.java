package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.SKOS;

import io.github.sylvainloiseau.lameta2rico.lameta.LametaLayout;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Contribution;
import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Folder;
import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RicRst;

/**
 * A {@code Sessions/<name>} folder → a {@code rico:RecordSet} for the folder and a
 * {@code rico:Activity} for the recording event it documents (plan §5.1, §5.4).
 */
final class SessionMapper {

    private final ConversionContext ctx;
    private final DocumentMapper documents;
    private final ContributionMapper contributions;

    SessionMapper(ConversionContext ctx, DocumentMapper documents) {
        this.ctx = ctx;
        this.documents = documents;
        this.contributions = new ContributionMapper(ctx);
    }

    void map(Folder folder) {
        IRI s = ctx.uris.session(folder.dirName());
        IRI a = ctx.uris.sessionActivity(folder.dirName());

        ctx.sink.add(ctx.uris.projectFolder(LametaLayout.SESSIONS_DIR), RICO.includesOrIncluded, s);
        ctx.sink.type(s, RICO.RecordSet);
        ctx.sink.add(s, RICO.hasRecordSetType, RicRst.File);
        ctx.sink.add(s, RICO.documents, a);
        ctx.sink.type(a, RICO.Activity);

        XmlElement metadata = folder.metadata();
        FieldCursor f = new FieldCursor(metadata);
        f.text("id").ifPresent(v -> {
            ctx.sink.literal(s, RICO.identifier, v);
            ctx.sink.literal(a, RICO.identifier, v);
        });
        f.text("title").ifPresent(v -> {
            ctx.sink.literal(s, RICO.title, v);
            ctx.sink.literal(a, RICO.name, v);
        });

        List<IRI> languages = new ArrayList<>();
        for (String field : List.of("languages", "workingLanguages")) {
            f.text(field).ifPresent(v -> splitList(v, "[;,]").forEach(code -> languages.add(ctx.vocabulary.language(code))));
        }
        languages.forEach(l -> ctx.sink.add(s, RICO.hasOrHadSomeMembersWithLanguage, l));

        Optional<IRI> genre = f.text("genre").map(v -> activityType("genre", v));
        genre.ifPresent(g -> ctx.sink.add(a, RICO.hasActivityType, g));
        f.text("Sub-Genre").ifPresent(v -> {
            IRI sub = activityType("subGenre", v);
            ctx.sink.add(a, RICO.hasActivityType, sub);
            genre.ifPresent(g -> ctx.sink.add(sub, SKOS.BROADER, g));
        });

        Optional<String> location = f.text("location");
        f.text("access").ifPresent(v -> {
            IRI rule = ctx.vocabulary.term("rule", "access", v, RICO.Rule);
            ctx.sink.add(s, RICO.isOrWasRegulatedBy, rule);
            ctx.accessProtocol.ifPresent(p -> ctx.sink.add(rule, RICO.isOrWasPartOf, p));
        });
        f.text("accessDescription").ifPresent(v -> ctx.sink.literal(s, RICO.conditionsOfAccess, v));
        f.text("synopsis").ifPresent(v -> {
            ctx.sink.literal(s, RICO.scopeAndContent, v);
            ctx.sink.literal(a, RICO.generalDescription, v);
        });
        f.text("status").ifPresent(v -> ctx.sink.add(s, RICO.hasOrHadAllMembersWithRecordState,
                ctx.vocabulary.term("recordState", null, v, RICO.RecordState)));
        f.text("date").ifPresent(v -> {
            ctx.sink.date(a, RICO.date, v);
            ctx.sink.date(s, RICO.creationDate, v);
        });
        f.text("keyword").ifPresent(v -> splitList(v, ",").forEach(k -> ctx.sink.add(s, RICO.hasOrHadSubject,
                ctx.vocabulary.term("concept", "keyword", k, RICO.Concept))));
        f.text("topic").ifPresent(v -> ctx.sink.add(s, RICO.hasOrHadMainSubject,
                ctx.vocabulary.term("concept", "topic", v, RICO.Concept)));
        f.text("notes").ifPresent(v -> ctx.sink.literal(s, SKOS.NOTE, v));
        f.text("participants").ifPresent(v -> splitList(v, ";")
                .forEach(p -> ctx.sink.add(a, RICO.hasOrHadParticipant, ctx.agents.resolvePerson(p))));
        f.consume("contributions");
        contributions.map(s, Contribution.from(metadata), Optional.of(a));

        // AdditionalFields: activity categories and place hierarchy
        XmlElement additional = f.child("AdditionalFields").orElse(XmlElement.empty("AdditionalFields"));
        FieldCursor af = new FieldCursor(additional);
        af.text("Involvement").ifPresent(v -> ctx.sink.add(a, RICO.hasActivityType, activityType("involvement", v)));
        af.text("Planning_Type").ifPresent(v -> ctx.sink.add(a, RICO.hasActivityType, activityType("planningType", v)));
        af.text("Social_Context").ifPresent(v -> ctx.sink.add(a, RICO.hasActivityType, activityType("socialContext", v)));
        ctx.vocabulary.placeChain(location.isPresent(),
                        location.orElse(null),
                        af.text("Location_Region").orElse(null),
                        af.text("Location_Country").orElse(null),
                        af.text("Location_Continent").orElse(null))
                .ifPresent(place -> ctx.sink.add(a, RICO.hasOrHadLocation, place));
        ctx.unmapped.handle(s, af.remaining());

        f.child("CustomFields").ifPresent(cf -> ctx.unmapped.handleCustomFields(s, cf));
        ctx.unmapped.handle(s, f.remaining());

        for (DocumentFile d : folder.files()) {
            documents.map(d, s, folder.dirName(), languages, Optional.empty());
        }
    }

    private IRI activityType(String facet, String value) {
        return ctx.vocabulary.term("activityType", facet, value, RICO.ActivityType);
    }

    /** Splits on {@code separatorRegex}, trims, drops blanks and duplicates, keeps order. */
    static List<String> splitList(String value, String separatorRegex) {
        Set<String> items = new LinkedHashSet<>();
        Arrays.stream(value.split(separatorRegex)).map(String::strip).filter(x -> !x.isEmpty()).forEach(items::add);
        return List.copyOf(items);
    }
}

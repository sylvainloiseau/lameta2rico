package fr.cnrs.lacito.lameta2rdf.rico;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.eclipse.rdf4j.model.IRI;

import fr.cnrs.lacito.lameta2rdf.lameta.model.Contribution;
import fr.cnrs.lacito.lameta2rdf.vocabulary.RICO;

/** {@code <contributions>} → n-ary {@code rico:CreationRelation} nodes (plan §5.7). */
final class ContributionMapper {

    /** Lameta's placeholder for "no date". */
    static final String NO_DATE = "0001-01-01";

    /** Roles for which the agent is also linked by the {@code rico:hasCreator} shortcut. */
    static final Set<String> AUTHOR_LIKE_ROLES = Set.of(
            "author", "compiler", "recorder", "transcriber", "translator", "annotator", "researcher");

    private final ConversionContext ctx;

    ContributionMapper(ConversionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * @param source   the record resource the contributions are about
     * @param activity the session activity, whose participants the contributors also are
     */
    void map(IRI source, List<Contribution> contributions, Optional<IRI> activity) {
        int rank = 0;
        for (Contribution c : contributions) {
            rank++;
            IRI agent = ctx.agents.resolvePerson(c.name());
            IRI relation = ctx.uris.creation(source, rank);
            ctx.sink.type(relation, RICO.CreationRelation);
            ctx.sink.add(relation, RICO.relationHasSource, source);
            ctx.sink.add(relation, RICO.relationHasTarget, agent);
            c.role().ifPresent(role -> ctx.sink.add(relation, RICO.creationWithRole, roleType(role)));
            c.smxRole()
                    .filter(r -> !r.equalsIgnoreCase("unspecified"))
                    .ifPresent(role -> ctx.sink.add(relation, RICO.creationWithRole, roleType(role)));
            c.date().filter(d -> !d.equals(NO_DATE)).ifPresent(d -> ctx.sink.date(relation, RICO.date, d));
            c.comments().ifPresent(text -> ctx.sink.literal(relation, RICO.generalDescription, text));
            if (c.role().map(r -> AUTHOR_LIKE_ROLES.contains(r.toLowerCase(Locale.ROOT))).orElse(false)) {
                ctx.sink.add(source, RICO.hasCreator, agent);
            }
            activity.ifPresent(a -> ctx.sink.add(a, RICO.hasOrHadParticipant, agent));
        }
    }

    private IRI roleType(String role) {
        return ctx.vocabulary.term("roleType", null, role, RICO.RoleType);
    }
}

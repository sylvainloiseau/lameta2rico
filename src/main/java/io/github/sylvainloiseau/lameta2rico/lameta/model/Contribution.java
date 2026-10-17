package io.github.sylvainloiseau.lameta2rico.lameta.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;

/**
 * One {@code <contributor>} of a {@code <contributions>} element. Empty values are
 * represented by {@link Optional#empty()}.
 *
 * @param name     the person's code or name, as typed in Lameta
 * @param role     OLAC role ({@code participant}, {@code speaker}, {@code author}...)
 * @param smxRole  legacy SayMore role, often {@code unspecified}
 * @param date     contribution date as written ({@code 0001-01-01} means "no date")
 * @param comments free text
 */
public record Contribution(String name, Optional<String> role, Optional<String> smxRole, Optional<String> date,
        Optional<String> comments) {

    /** Contributions listed under {@code parent}'s {@code <contributions>} child, skipping nameless ones. */
    public static List<Contribution> from(XmlElement parent) {
        List<Contribution> result = new ArrayList<>();
        parent.child("contributions").ifPresent(contributions -> {
            for (XmlElement c : contributions.children("contributor")) {
                c.childText("name").ifPresent(name -> result.add(new Contribution(name,
                        c.childText("role"), c.childText("smxrole"), c.childText("date"), c.childText("comments"))));
            }
        });
        return result;
    }
}

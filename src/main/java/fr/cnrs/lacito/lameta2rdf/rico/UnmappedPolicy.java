package fr.cnrs.lacito.lameta2rdf.rico;

import java.util.Locale;

/** What to do with metadata fields that have no RiC-O equivalent (plan §5.8). */
public enum UnmappedPolicy {
    /** Emit {@code rico:generalDescription "<label>: <value>"}. */
    DESCRIPTION,
    /** Emit nothing. */
    DROP;

    /** Parses {@code description} or {@code drop}, case-insensitively. */
    public static UnmappedPolicy parse(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for unmapped: '" + value + "' (expected description or drop)");
        }
    }
}

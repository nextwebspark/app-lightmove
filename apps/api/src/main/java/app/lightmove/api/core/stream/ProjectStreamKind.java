package app.lightmove.api.core.stream;

import java.util.Locale;
import java.util.Optional;

/** What changed under a mandate. The client treats every kind the same way — refetch — so a new kind
 * costs nothing on the frontend; the enum exists so a publish site cannot invent a misspelt one. */
public enum ProjectStreamKind {
    CANDIDATE_CAPTURED,
    CANDIDATE_ENRICHED,
    COMPANY_CAPTURED,
    COMPANY_ENRICHED;

    /**
     * The wire form the browser sees: {@code candidate-enriched}. {@code Locale.ROOT} because the
     * default locale is the JVM's: under a Turkish one {@code CANDIDATE} lowercases to
     * {@code candıdate}, and the frontend stops recognising its own events.
     */
    public String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The kind a {@code NOTIFY} payload names, or empty when it names nothing this version knows. */
    public static Optional<ProjectStreamKind> fromWire(String wire) {
        for (ProjectStreamKind kind : values()) {
            if (kind.wire().equals(wire)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}

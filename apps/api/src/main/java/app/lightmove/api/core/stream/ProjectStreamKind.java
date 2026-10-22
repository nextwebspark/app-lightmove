package app.lightmove.api.core.stream;

import java.util.Locale;
import java.util.Optional;

/** What changed under a mandate. The client treats every kind the same way — refetch — so a new kind
 * costs nothing on the frontend; the enum exists so a publish site cannot invent a misspelt one. */
public enum ProjectStreamKind {
    CANDIDATE_CAPTURED,
    CANDIDATE_ENRICHED,
    /** An executive changed Companies stage, alone or with their company: both halves of a stage page move. */
    CANDIDATE_MOVED,
    COMPANY_CAPTURED,
    COMPANY_ENRICHED,
    /** A Find executives run moved: started, a company finished, or the run ended. */
    EXECUTIVE_SOURCING,
    /** An email, a reply or a sequence run moved. Staff-only: a client seat is never told outreach happened. */
    OUTREACH,
    /** A task was filed, changed or closed. Staff-only, as outreach is. */
    TASK,
    /** A Notetaker bot on a call with an executive moved: joined, recording, left, or its note filed. Staff-only. */
    NOTETAKER;

    /** Sent only to a stream whose holder may work the position, never to one that may only read it. */
    public boolean isStaffOnly() {
        return this == OUTREACH || this == TASK || this == NOTETAKER;
    }

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

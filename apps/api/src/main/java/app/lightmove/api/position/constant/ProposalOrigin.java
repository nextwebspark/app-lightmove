package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * What a proposal's <b>value</b> was drawn from — distinct from {@link ExtractionSource}, which says
 * how the whole document was read. Every proposal answers {@link #DOCUMENT} today: template backfill,
 * which once produced {@link #TEMPLATE} for a field the document said nothing about, was retired as
 * dead weight under auto-fill — a mandate is already seeded from its matched template, so proposing
 * that same value back stamped {@code TEMPLATE} would be a no-op, and stamping it {@code DOCUMENT}
 * would draw a marker over a value with no snippet to point at. {@link #TEMPLATE} stays on the wire
 * for now rather than being deleted outright.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProposalOrigin {

    /** Read from the document itself, by the model or, on step one, the heuristic fallback. */
    DOCUMENT("document"),

    /** No longer produced by any proposer — see the class doc. */
    TEMPLATE("template");

    private final String value;
}

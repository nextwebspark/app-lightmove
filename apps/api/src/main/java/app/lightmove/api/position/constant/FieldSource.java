package app.lightmove.api.position.constant;

/**
 * Where a brief field's current value came from: the matched role template, a document reading, or a
 * person typing it in. Persisted per row on every owned list and, for the ten scalars a template or a
 * document reading can plausibly claim, in {@code Position.fieldSources}.
 *
 * <p>Distinct from {@link ProposalOrigin}, which labels an unaccepted "Read from document" proposal —
 * that one has no {@code MANUAL}, because a proposal is never a person's own value until it is
 * written here.
 */
public enum FieldSource {

    /** Drafted by the matched role template. */
    TEMPLATE,

    /** Filled from the attached position description. */
    DOCUMENT,

    /** Typed by a person. The default for anything a caller does not otherwise mark. */
    MANUAL;

    /** A null item source on the wire always means {@code MANUAL} — a person typed it. */
    public static FieldSource orManual(FieldSource source) {
        return source == null ? MANUAL : source;
    }
}

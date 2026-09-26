package app.lightmove.api.position.constant;

/**
 * Where a brief field's current value came from. Distinct from {@link ProposalOrigin}, which labels an
 * unaccepted proposal and so has no {@code MANUAL}.
 */
public enum FieldSource {

    TEMPLATE,
    DOCUMENT,
    MANUAL;

    /** A null item source on the wire always means {@code MANUAL}. */
    public static FieldSource orManual(FieldSource source) {
        return source == null ? MANUAL : source;
    }
}

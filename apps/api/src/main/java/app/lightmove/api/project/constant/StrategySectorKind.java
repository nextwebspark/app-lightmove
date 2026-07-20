package app.lightmove.api.project.constant;

/**
 * How a sector came to be on the strategy. DIRECT is the consultant's chosen core sector (a must-have
 * filter); ADJACENT and INFERRED are AI suggestions — adjacent sectors of transferable experience and
 * inferred tags that widen the talent pool. All three persist in one ordered collection keyed by this.
 */
public enum StrategySectorKind {
    DIRECT,
    ADJACENT,
    INFERRED
}

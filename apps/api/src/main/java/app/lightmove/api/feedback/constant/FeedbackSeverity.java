package app.lightmove.api.feedback.constant;

/**
 * How badly a bug is hurting the tester, in their own judgement.
 *
 * <p>Carried on a feature request too, where it reads as how much they want it. One scale rather than
 * two keeps the triage label set small enough to actually be used.
 */
public enum FeedbackSeverity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

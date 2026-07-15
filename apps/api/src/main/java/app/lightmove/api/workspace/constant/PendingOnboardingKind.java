package app.lightmove.api.workspace.constant;

/**
 * Which branch of signup step 2 the user took, held until they verify their address.
 *
 * <p>The two are mutually exclusive and a user has at most one of them, which is why they live in one
 * row under one {@code UNIQUE (user_id)} rather than in two tables where "at most one" would be a rule
 * somebody has to remember to enforce.
 */
public enum PendingOnboardingKind {

    /** "I'll start my own." On verification: a workspace, with them as its ADMIN. */
    CREATE,

    /** "That's my firm." On verification: a PENDING_APPROVAL membership for an admin to decide on. */
    JOIN
}

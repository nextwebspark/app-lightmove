package app.lightmove.api.core.security.model;

/**
 * What a proved credential earns: a session, or, for a person with a second factor, a challenge for the code.
 * Every door that signs someone in with a password or a mailbox answers one of these, never a session directly.
 */
public sealed interface SignInOutcome permits AuthenticatedSession, MfaChallengeIssued {
}

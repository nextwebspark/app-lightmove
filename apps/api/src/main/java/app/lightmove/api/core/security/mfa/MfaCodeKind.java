package app.lightmove.api.core.security.mfa;

/** Which kind of code passed: the audit trail tells a phone from a recovery code, the person is told nothing. */
public enum MfaCodeKind {
    AUTHENTICATOR,
    RECOVERY_CODE
}

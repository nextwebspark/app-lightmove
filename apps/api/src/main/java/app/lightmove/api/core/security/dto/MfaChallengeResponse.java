package app.lightmove.api.core.security.dto;

/** A right password that still owes a code. Nothing here is a credential: the challenge is in an httpOnly cookie. */
public record MfaChallengeResponse(boolean mfaRequired, long expiresIn) {
}

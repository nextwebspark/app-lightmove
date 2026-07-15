package app.lightmove.api.core.security.constant;

/**
 * What a one-shot token entitles the bearer to do.
 *
 * <p>Purpose is stored and checked on redemption so that a token minted for one flow cannot be spent
 * in another — an email-verification link must never double as a password reset.
 */
public enum TokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}

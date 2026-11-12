package app.lightmove.api.account.constant;

/** How a person shows it is them before their account is deleted. */
public enum AccountProof {

    /** The current password, and a code where two-factor is on. */
    PASSWORD,

    /** An account with no password signed in moments ago, which a session stolen earlier cannot show. */
    RECENT_SIGN_IN
}

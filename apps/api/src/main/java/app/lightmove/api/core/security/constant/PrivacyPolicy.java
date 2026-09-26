package app.lightmove.api.core.security.constant;

/** The privacy policy a new account agrees to, recorded against the user so we can prove it later. */
public final class PrivacyPolicy {

    /** One value for every signup door, so password and OAuth accounts can never record different ones. */
    public static final String CURRENT_VERSION = "2026-07-01";

    private PrivacyPolicy() {}
}

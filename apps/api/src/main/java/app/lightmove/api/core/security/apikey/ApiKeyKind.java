package app.lightmove.api.core.security.apikey;

/** Whose key it is: one member's, bounded by what they can open, or the workspace's, reading every position. */
public enum ApiKeyKind {

    /** One member's. Never reads more than its owner can open now, and dies with their membership. */
    PERSONAL("uncava_pat_"),

    /** The workspace's, made by an admin. Reads every position and outlives whoever made it. */
    SERVICE("uncava_svc_");

    private final String secretPrefix;

    ApiKeyKind(String secretPrefix) {
        this.secretPrefix = secretPrefix;
    }

    /** Fixed so a leaked key is recognisable, to a person and to secret scanning, by its first characters. */
    public String secretPrefix() {
        return secretPrefix;
    }
}

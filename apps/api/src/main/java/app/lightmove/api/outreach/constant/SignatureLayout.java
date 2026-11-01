package app.lightmove.api.outreach.constant;

/** How an email signature is laid out; each is drawn by {@code EmailSignatureRenderer}. */
public enum SignatureLayout {
    /** The details as text, no pictures. */
    CLASSIC,
    /** The person's photo beside their details. */
    PHOTO,
    /** Photo, details, then the company logo past a rule. */
    PHOTO_LOGO,
    /** The details over the company logo. */
    LOGO_BANNER,
    /** One line, for replies. */
    MINIMAL,
    /** The person's own words, written in the notes' editor. */
    CUSTOM;

    public boolean drawsPhoto() {
        return this == PHOTO || this == PHOTO_LOGO;
    }

    public boolean drawsLogo() {
        return this == PHOTO_LOGO || this == LOGO_BANNER || this == CUSTOM;
    }
}

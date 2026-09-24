package app.lightmove.api.core.email.render;

/**
 * The one place an email's colours and type are decided — the Uncava light palette, from
 * {@code claude-design/uncava-tokens.css}.
 *
 * <p>Light only. A mail client's dark mode is its own inversion and cannot be reached from here, so
 * the header pairs the mark with the wordmark as text: whatever a client does to the picture, the
 * brand still reads.
 *
 * <p>The font is the system stack rather than the product's Geist — a webfont does not load in most
 * mail clients, and a half-loaded brand font is worse than none.
 */
final class EmailPalette {

    static final String FONT = "-apple-system,system-ui,'Segoe UI',Helvetica,Arial,sans-serif";

    static final String PAGE = "#F7F8FA";
    static final String CARD = "#FFFFFF";
    /** {@code --u-border} (rgba 3,7,18 at 9%) flattened onto the white card, since not every client blends alpha. */
    static final String BORDER = "#E8E9EA";

    static final String HEADING = "#0B0D12";
    static final String BODY = "#4C5462";
    static final String MUTED = "#6B7382";

    /** {@code --u-accent-solid}: the one stop in the palette that carries white text. */
    static final String ACCENT = "#4A56D6";
    static final String ON_ACCENT = "#FFFFFF";

    private EmailPalette() {}
}

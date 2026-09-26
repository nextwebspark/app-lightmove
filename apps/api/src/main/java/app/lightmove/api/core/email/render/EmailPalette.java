package app.lightmove.api.core.email.render;

/**
 * An email's colours and type — the Uncava light palette. Light only, since a client's dark mode
 * cannot be reached, and the system font stack, since webfonts rarely load in mail clients.
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

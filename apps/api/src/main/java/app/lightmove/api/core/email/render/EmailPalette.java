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

    /** The tints below are the app's {@code --u-*-tint} tokens flattened onto white, as {@link #BORDER} is. */
    static final String ACCENT_TINT = "#EFF0FB";
    static final String ACCENT_TINT_RULE = "#DCDEF3";

    /** {@code --u-signal}: the app's warning role, which is what late work is. */
    static final String SIGNAL = "#A05B2D";
    static final String SIGNAL_TINT = "#F6EFEA";

    /** {@code --u-raised} and {@code --u-text2}. */
    static final String NEUTRAL_TINT = "#ECEFF3";
    static final String NEUTRAL_TEXT = "#343A46";
    static final String LABEL = "#525967";

    private EmailPalette() {}
}

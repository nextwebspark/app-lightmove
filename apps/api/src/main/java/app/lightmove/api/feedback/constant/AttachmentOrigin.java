package app.lightmove.api.feedback.constant;

/**
 * Where an image on a report came from.
 *
 * <p>Recorded because the two are read differently: a capture is the screen at the moment the tester
 * opened the widget, while an upload is whatever they chose to add — often a photo of a second device,
 * or a shot taken minutes earlier on a different page.
 */
public enum AttachmentOrigin {

    /** Rendered from the page behind the form when the widget opened. */
    SCREEN_CAPTURE("Screen at the time of the report"),

    /** Chosen by the tester from their own files. */
    UPLOAD("Added by the reporter");

    private final String label;

    AttachmentOrigin(String label) {
        this.label = label;
    }

    /** What the issue calls it. The enum name reads as shouting in a sentence somebody has to read. */
    public String label() {
        return label;
    }
}

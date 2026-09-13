package app.lightmove.api.position.constant;

/**
 * What a step-one proposal's <b>value</b> was drawn from — distinct from {@link ExtractionSource},
 * which says how the whole document was read. A field can come straight from the document (or the
 * model's reading of it) or, when the document said nothing about it, from the role title's
 * matched brief template.
 */
public enum ProposalOrigin {

    /** Read from the document itself, by the model or the heuristic fallback alike. */
    DOCUMENT("document"),

    /** The document said nothing about this field; the value is the matched template's own. */
    TEMPLATE("template");

    private final String wireToken;

    ProposalOrigin(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }
}

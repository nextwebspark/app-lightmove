package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;

/**
 * One proposed value for a readable step's field.
 *
 * @param fieldKey a step's own field name; a repeatable key is one row per item, each with its snippet
 * @param value    in the wire format that step's {@code Put...Request} expects — an enum's Java name
 * @param snippet  the source sentence, or null when none could be verified against the document
 * @param origin   always {@link ProposalOrigin#DOCUMENT} today
 */
public record ExtractedField(String fieldKey, String value, ProposalConfidence confidence, String snippet,
                             ProposalOrigin origin) {

    public ExtractedField withConfidence(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, snippet, origin);
    }

    /** For a snippet that failed verification: a paraphrase, not a quote. */
    public ExtractedField withoutSnippet(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, null, origin);
    }
}

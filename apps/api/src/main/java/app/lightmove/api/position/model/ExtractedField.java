package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;

/**
 * One proposed value for a field of any readable step, with its confidence and source sentence.
 *
 * @param fieldKey a step's own field name; a repeatable key (responsibility, priority, direct report,
 *                 criterion, competency) is one row per item, so each carries its own snippet
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

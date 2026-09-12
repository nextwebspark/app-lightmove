package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ProposalConfidence;

/**
 * One proposed value for a step-one field, with the confidence it deserves and the sentence it came
 * from.
 *
 * @param fieldKey   {@code roleTitle}, {@code department}, {@code location}, {@code employmentType},
 *                   {@code seniority}, {@code narrative} or {@code responsibility} — the last one row
 *                   per responsibility rather than a list, so each carries its own snippet and can be
 *                   accepted or dismissed on its own
 * @param value      the proposed value, in the wire format {@code PutPositionDetailsRequest} expects
 *                   for that field — an enum's Java name for {@code employmentType}/{@code seniority}
 * @param snippet    the sentence in the document this reading came from, or null when none could be
 *                   verified against the source text
 */
public record ExtractedField(String fieldKey, String value, ProposalConfidence confidence, String snippet) {

    public ExtractedField withConfidence(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, snippet);
    }

    /** The snippet failed verification: it is a paraphrase, not a quote, so it is dropped and trust drops with it. */
    public ExtractedField withoutSnippet(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, null);
    }
}

package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.constant.ProposalOrigin;

/**
 * One proposed value for a field of any readable step, with the confidence it deserves and the
 * sentence it came from.
 *
 * @param fieldKey   one of a step's own field names — {@code roleTitle}, {@code department},
 *                   {@code locationCity}, {@code locationCountry}, {@code employmentType},
 *                   {@code seniority}, {@code narrative} or
 *                   {@code responsibility} for step one; {@code mandateReason}, {@code businessDriver}
 *                   or {@code strategicPriority} for step two; {@code reportsToTitle},
 *                   {@code directReportTitle}, {@code teamSize} or {@code noticePeriod} for step
 *                   three; {@code requiredCriterion}, {@code preferredCriterion},
 *                   {@code technicalCompetency} or {@code behaviouralCompetency} for step five — a
 *                   repeatable key (a responsibility, a priority, a direct report, a criterion, a
 *                   competency) is one row per item rather than a list, so each carries its own
 *                   snippet and can be accepted or dismissed on its own
 * @param value      the proposed value, in the wire format that step's {@code Put...Request} expects
 *                   for that field — an enum's Java name for an enum-valued field
 * @param snippet    the sentence in the document this reading came from, or null when none could be
 *                   verified against the source text
 * @param origin     always {@link ProposalOrigin#DOCUMENT} today — read or inferred by the model or, on
 *                   step one, the heuristic fallback. {@link ProposalOrigin#TEMPLATE} is never proposed
 *                   since template backfill was retired: a value the document said nothing about stays
 *                   unproposed rather than drawing on the mandate's already-seeded template.
 */
public record ExtractedField(String fieldKey, String value, ProposalConfidence confidence, String snippet,
                             ProposalOrigin origin) {

    public ExtractedField withConfidence(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, snippet, origin);
    }

    /** The snippet failed verification: it is a paraphrase, not a quote, so it is dropped and trust drops with it. */
    public ExtractedField withoutSnippet(ProposalConfidence confidence) {
        return new ExtractedField(fieldKey, value, confidence, null, origin);
    }
}

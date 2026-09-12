package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description for step three — raw and
 * unchecked. See {@link ModelDetailsAnswer} for why this is named for where it came from rather than
 * for what it proposes; turning it into {@link ExtractedField} rows happens in
 * {@link app.lightmove.api.position.service.PositionReportingProposer}.
 *
 * <p>{@code reportsToTitle} is a job title, never a person's name — the prompt is explicit that a
 * document naming a manager by name ("Reporting to Ahmed Al-Mansoori, Group CEO") must answer with the
 * title alone. {@code noticeValue} travels as a string like every other numeric field the model
 * answers: a junk token never fails JSON binding, it is parsed and validated in the proposer.
 */
public record ModelReportingAnswer(
        String reportsToTitle,
        String reportsToTitleSnippet,
        List<ModelDirectReport> directReports,
        String teamSize,
        String teamSizeSnippet,
        String noticeValue,
        String noticeValueSnippet,
        String noticeUnit,
        String noticeUnitSnippet
) {

    /**
     * One seat reporting to the mandate. A multiplier in the document's own wording ("Assistant
     * Manager x 2") is expanded into two entries with the same title by the prompt, not packed into
     * one entry with a count — see {@code PositionReportingProposer}'s class doc for why.
     */
    public record ModelDirectReport(String title, String snippet) {}
}

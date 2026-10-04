package app.lightmove.api.position.model;

import java.util.List;

/**
 * The model's raw, unchecked reading for step three. {@code reportsToTitle} is a job title, never a
 * person's name; {@code noticeValue} is a string so a junk token never fails JSON binding.
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

    /** "Assistant Manager x 2" is two entries (see {@code position-extract-reporting-system.st}). */
    public record ModelDirectReport(String title, String snippet) {}
}

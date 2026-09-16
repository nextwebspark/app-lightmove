package app.lightmove.api.position.model;

import java.util.List;

/**
 * What the model answered when asked to read a position description — raw and unchecked.
 *
 * <p>Named for where it came from rather than for what it proposes, exactly as {@code
 * ModelMappingAnswer} sits beside {@code ProposedColumnMappings} in {@code dataimport}: this is what a
 * model said, and turning it into {@link ExtractedField} rows — re-hydrating what was redacted,
 * verifying each snippet, refusing anything that will not resolve — happens in
 * {@link app.lightmove.api.position.service.PositionDetailsProposer}.
 */
public record ModelDetailsAnswer(
        String roleTitle,
        String roleTitleSnippet,
        String department,
        String departmentSnippet,
        String location,
        String locationSnippet,
        String employmentType,
        String employmentTypeSnippet,
        String seniority,
        String senioritySnippet,
        List<ModelResponsibility> responsibilities,
        String narrative,
        String narrativeSnippet
) {

    /** One responsibility line and the sentence it was read from. */
    public record ModelResponsibility(String text, String snippet) {}
}

package app.lightmove.api.position.model;

import java.util.List;

/** The model's raw, unchecked reading for step one, verified into {@link ExtractedField} rows by the proposer. */
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

    public record ModelResponsibility(String text, String snippet) {}
}

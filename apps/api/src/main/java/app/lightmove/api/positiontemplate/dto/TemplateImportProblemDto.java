package app.lightmove.api.positiontemplate.dto;

/** {@code field} is a path inside the template, e.g. {@code body.competencies}. */
public record TemplateImportProblemDto(String field, String message) {
}

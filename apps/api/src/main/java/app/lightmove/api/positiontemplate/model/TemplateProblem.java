package app.lightmove.api.positiontemplate.model;

/** One thing wrong with a template, against the path of the field it is wrong about. */
public record TemplateProblem(String field, String message) {
}

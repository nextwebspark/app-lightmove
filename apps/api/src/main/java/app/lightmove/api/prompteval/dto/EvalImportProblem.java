package app.lightmove.api.prompteval.dto;

/** One thing wrong with one line of an import file. {@code line} is its line in the file. */
public record EvalImportProblem(int line, String message) {
}

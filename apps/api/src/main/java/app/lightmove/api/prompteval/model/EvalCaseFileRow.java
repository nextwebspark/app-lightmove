package app.lightmove.api.prompteval.model;

import java.util.List;

/**
 * One line of an import file: the case it holds, or what is wrong with it. {@code line} is its line in the file, so
 * it can be found there; {@code read} is null when the line could not be read as a case at all.
 */
public record EvalCaseFileRow(int line, NewEvalCase read, List<String> problems) {

    public boolean isImportable() {
        return problems.isEmpty();
    }
}

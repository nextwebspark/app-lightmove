package app.lightmove.api.prompteval.dto;

import java.util.List;

/**
 * What an import would do (preview) or did (commit). {@code added} cases are new to the dataset, split into
 * {@code dev} and {@code holdout}; {@code alreadyPresent} rows name a key the dataset holds and are left alone.
 * Nothing is added while {@code problems} is not empty.
 */
public record EvalCaseImportResponse(
        boolean committed,
        int rows,
        int added,
        int alreadyPresent,
        int dev,
        int holdout,
        List<EvalImportProblem> problems
) {
}

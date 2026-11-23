package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.dto.EvalCaseImportResponse;
import app.lightmove.api.prompteval.dto.EvalImportProblem;
import app.lightmove.api.prompteval.model.EvalCaseFileRow;
import app.lightmove.api.prompteval.model.EvalDataset;
import app.lightmove.api.prompteval.model.NewEvalCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Adds synthetic cases to a dataset from a JSONL file: a preview that writes nothing, then a commit that re-reads
 * the same file and adds its new cases, all or nothing. A row naming a key the dataset holds is left alone, since a
 * case is never rewritten.
 */
@Service
@RequiredArgsConstructor
public class EvalCaseImportService {

    private final EvalDatasetService datasets;
    private final EvalDatasetStore store;
    private final EvalSubjects subjects;
    private final EvalCaseFileReader reader;
    private final TransactionTemplate transactions;
    private final AuditService audit;

    public EvalCaseImportResponse preview(UUID datasetId, MultipartFile file) {
        List<EvalCaseFileRow> rows = read(datasetId, file);
        return responseOf(false, rows, newCasesOf(rows, store.keysOf(datasetId)));
    }

    public EvalCaseImportResponse commit(UUID userId, UUID datasetId, MultipartFile file,
                                         HttpServletRequest httpRequest) {
        List<EvalCaseFileRow> rows = read(datasetId, file);
        if (rows.stream().anyMatch(row -> !row.isImportable())) {
            throw ApiException.of(ErrorCode.EVAL_IMPORT_INVALID);
        }
        List<NewEvalCase> added = transactions.execute(status -> {
            store.lockDataset(datasetId);
            List<NewEvalCase> newCases = newCasesOf(rows, store.keysOf(datasetId));
            store.add(datasetId, newCases, userId);
            return newCases;
        });
        audit.event(PlatformEventType.EVAL_CASES_IMPORTED).actor(userId)
                .target("eval_dataset", datasetId)
                .detail("added", added.size())
                .detail("alreadyPresent", rows.size() - added.size())
                .from(httpRequest)
                .record();
        return responseOf(true, rows, added);
    }

    private List<EvalCaseFileRow> read(UUID datasetId, MultipartFile file) {
        EvalDataset dataset = datasets.require(datasetId);
        EvalSubject subject = subjects.of(dataset.promptId())
                .orElseThrow(() -> ApiException.of(ErrorCode.EVAL_SUBJECT_UNAVAILABLE));
        return reader.read(file, subject);
    }

    private static List<NewEvalCase> newCasesOf(List<EvalCaseFileRow> rows, Set<String> heldKeys) {
        return rows.stream()
                .filter(EvalCaseFileRow::isImportable)
                .map(EvalCaseFileRow::read)
                .filter(read -> !heldKeys.contains(read.key()))
                .toList();
    }

    private static EvalCaseImportResponse responseOf(boolean committed, List<EvalCaseFileRow> rows,
                                                     List<NewEvalCase> added) {
        int importable = (int) rows.stream().filter(EvalCaseFileRow::isImportable).count();
        return new EvalCaseImportResponse(committed, rows.size(), added.size(), importable - added.size(),
                countOf(added, EvalCaseSplit.DEV), countOf(added, EvalCaseSplit.HOLDOUT),
                rows.stream()
                        .flatMap(row -> row.problems().stream()
                                .map(problem -> new EvalImportProblem(row.line(), problem)))
                        .toList());
    }

    private static int countOf(List<NewEvalCase> cases, EvalCaseSplit split) {
        return (int) cases.stream().filter(added -> added.split() == split).count();
    }
}

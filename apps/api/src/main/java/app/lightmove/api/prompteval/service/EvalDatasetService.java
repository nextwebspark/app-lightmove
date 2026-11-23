package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.prompteval.dto.EvalCaseSummary;
import app.lightmove.api.prompteval.dto.EvalDatasetDetail;
import app.lightmove.api.prompteval.dto.EvalDatasetOverview;
import app.lightmove.api.prompteval.model.EvalCase;
import app.lightmove.api.prompteval.model.EvalDataset;
import app.lightmove.api.prompteval.model.EvalRubric;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the platform console reads of the eval datasets, and the one change a case takes: being retired. */
@Service
@RequiredArgsConstructor
public class EvalDatasetService {

    private final EvalDatasetStore store;
    private final EvalSubjects subjects;
    private final EvalRubricStore rubrics;
    private final JudgeCalibrationService calibration;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<EvalDatasetOverview> list() {
        Map<UUID, EvalRubric> rubricByDataset = rubrics.latestByDataset();
        return store.datasets().stream()
                .map(dataset -> overviewOf(dataset, rubricByDataset.get(dataset.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvalDatasetDetail get(UUID datasetId) {
        EvalDataset dataset = require(datasetId);
        EvalRubric rubric = rubrics.latestOf(datasetId).orElse(null);
        return new EvalDatasetDetail(overviewOf(dataset, rubric),
                store.casesOf(datasetId).stream().map(EvalDatasetService::summaryOf).toList(),
                rubric == null ? null : calibration.detailOf(dataset, rubric));
    }

    /** Retiring a case twice is the first retirement: the runs that scored it still name it either way. */
    @Transactional
    public EvalDatasetDetail retire(UUID userId, UUID caseId, HttpServletRequest httpRequest) {
        EvalCase retiring = store.caseOf(caseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No eval case " + caseId));
        if (store.retire(caseId, userId)) {
            audit.event(PlatformEventType.EVAL_CASE_RETIRED).actor(userId)
                    .target("eval_case", caseId)
                    .detail("dataset", retiring.datasetId())
                    .detail("caseKey", retiring.key())
                    .from(httpRequest)
                    .record();
        }
        return get(retiring.datasetId());
    }

    EvalDataset require(UUID datasetId) {
        return store.dataset(datasetId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No eval dataset " + datasetId));
    }

    private EvalDatasetOverview overviewOf(EvalDataset dataset, EvalRubric rubric) {
        return new EvalDatasetOverview(dataset.id(), dataset.name(), dataset.description(), dataset.promptId(),
                PlatformPrompt.of(dataset.promptId()).map(PlatformPrompt::getDisplayName).orElse(dataset.promptId()),
                dataset.cases(), dataset.dev(), dataset.holdout(), dataset.updatedAt(),
                subjects.of(dataset.promptId()).isPresent(), calibration.summaryOf(rubric).orElse(null));
    }

    private static EvalCaseSummary summaryOf(EvalCase evalCase) {
        return new EvalCaseSummary(evalCase.id(), evalCase.key(), evalCase.name(), evalCase.tag(), evalCase.split(),
                evalCase.addedBy(), evalCase.addedAt(), evalCase.retiredAt());
    }
}

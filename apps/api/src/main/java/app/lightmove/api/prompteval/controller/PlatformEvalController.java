package app.lightmove.api.prompteval.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.prompteval.dto.EvalCalibrationPairs;
import app.lightmove.api.prompteval.dto.EvalCaseImportResponse;
import app.lightmove.api.prompteval.dto.EvalDatasetDetail;
import app.lightmove.api.prompteval.dto.EvalDatasetOverview;
import app.lightmove.api.prompteval.dto.LabelPairRequest;
import app.lightmove.api.prompteval.service.EvalCaseImportService;
import app.lightmove.api.prompteval.service.EvalDatasetService;
import app.lightmove.api.prompteval.service.JudgeCalibrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The eval datasets and their rubrics, for a platform super admin under {@code EVAL_RUN}. Every case is synthetic;
 * nothing here reads inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform/evals")
@RequiredArgsConstructor
public class PlatformEvalController {

    private final EvalDatasetService datasets;
    private final EvalCaseImportService imports;
    private final JudgeCalibrationService calibration;

    @GetMapping("/datasets")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public List<EvalDatasetOverview> list() {
        return datasets.list();
    }

    @GetMapping("/datasets/{datasetId}")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalDatasetDetail get(@PathVariable UUID datasetId) {
        return datasets.get(datasetId);
    }

    /** Reads the file and answers with what an import would do. Writes nothing. */
    @PostMapping("/datasets/{datasetId}/cases/import/preview")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalCaseImportResponse previewImport(@PathVariable UUID datasetId,
                                                @RequestParam("file") MultipartFile file) {
        return imports.preview(datasetId, file);
    }

    /** Re-reads the same file and adds its new cases, all or nothing. */
    @PostMapping("/datasets/{datasetId}/cases/import/commit")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalCaseImportResponse commitImport(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID datasetId,
                                               @RequestParam("file") MultipartFile file,
                                               HttpServletRequest httpRequest) {
        return imports.commit(principal.userId(), datasetId, file, httpRequest);
    }

    /** The pairs a person labels for the dataset's rubric, with the picks made so far. Empty until started. */
    @GetMapping("/datasets/{datasetId}/rubric/pairs")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalCalibrationPairs pairs(@PathVariable UUID datasetId) {
        return calibration.pairs(datasetId);
    }

    /** Draws the pairs the first time; after that it answers the same ones. */
    @PostMapping("/datasets/{datasetId}/rubric/pairs")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalCalibrationPairs startLabelling(@PathVariable UUID datasetId) {
        return calibration.startLabelling(datasetId);
    }

    @PutMapping("/rubric-pairs/{pairId}/labels")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void label(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID pairId,
                      @Valid @RequestBody LabelPairRequest request, HttpServletRequest httpRequest) {
        calibration.label(principal.userId(), pairId, request, httpRequest);
    }

    /** Starts a check of the judge against the labels, and answers the dataset with the check going. */
    @PostMapping("/datasets/{datasetId}/rubric/check")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalDatasetDetail checkJudge(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID datasetId, HttpServletRequest httpRequest) {
        calibration.check(principal.userId(), datasetId, httpRequest);
        return datasets.get(datasetId);
    }

    @PostMapping("/cases/{caseId}/retire")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalDatasetDetail retire(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID caseId,
                                    HttpServletRequest httpRequest) {
        return datasets.retire(principal.userId(), caseId, httpRequest);
    }
}

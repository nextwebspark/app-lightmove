package app.lightmove.api.prompteval.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.prompteval.dto.EvalCaseImportResponse;
import app.lightmove.api.prompteval.dto.EvalDatasetDetail;
import app.lightmove.api.prompteval.dto.EvalDatasetOverview;
import app.lightmove.api.prompteval.service.EvalCaseImportService;
import app.lightmove.api.prompteval.service.EvalDatasetService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The eval datasets, for a platform super admin under {@code EVAL_RUN}. Every case is synthetic; nothing here reads
 * inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform/evals")
@RequiredArgsConstructor
public class PlatformEvalController {

    private final EvalDatasetService datasets;
    private final EvalCaseImportService imports;

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

    @PostMapping("/cases/{caseId}/retire")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalDatasetDetail retire(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID caseId,
                                    HttpServletRequest httpRequest) {
        return datasets.retire(principal.userId(), caseId, httpRequest);
    }
}

package app.lightmove.api.prompteval.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.prompteval.dto.EvalRunCases;
import app.lightmove.api.prompteval.dto.EvalRunDetail;
import app.lightmove.api.prompteval.dto.EvalRunEstimate;
import app.lightmove.api.prompteval.dto.EvalRunOverview;
import app.lightmove.api.prompteval.dto.StartEvalRunRequest;
import app.lightmove.api.prompteval.service.EvalRunService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The eval runs, for a platform super admin under {@code EVAL_RUN}: two versions of a prompt answering the same
 * synthetic cases. Nothing here reads inside a workspace.
 */
@RestController
@RequestMapping("/api/v1/platform/evals/runs")
@RequiredArgsConstructor
public class PlatformEvalRunController {

    private final EvalRunService runs;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public List<EvalRunOverview> list() {
        return runs.list();
    }

    /** What a start with the same body would take. Writes nothing and asks the model nothing. */
    @PostMapping("/estimate")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalRunEstimate estimate(@Valid @RequestBody StartEvalRunRequest request) {
        return runs.estimate(request);
    }

    @PostMapping
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    @ResponseStatus(HttpStatus.CREATED)
    public EvalRunDetail start(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody StartEvalRunRequest request, HttpServletRequest httpRequest) {
        return runs.start(principal.userId(), request, httpRequest);
    }

    @GetMapping("/{runId}")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalRunDetail get(@PathVariable UUID runId) {
        return runs.get(runId);
    }

    @GetMapping("/{runId}/cases")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalRunCases cases(@PathVariable UUID runId) {
        return runs.cases(runId);
    }

    @PostMapping("/{runId}/cancel")
    @RequirePlatformPermission(PlatformAction.EVAL_RUN)
    public EvalRunDetail cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID runId,
                                HttpServletRequest httpRequest) {
        return runs.cancel(principal.userId(), runId, httpRequest);
    }
}

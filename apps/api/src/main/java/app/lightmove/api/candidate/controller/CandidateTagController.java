package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CandidateTagResponse;
import app.lightmove.api.candidate.dto.CreateCandidateTagRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateTagRequest;
import app.lightmove.api.candidate.service.CandidateTagService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's tags on its people. Reading and adding one is any staff member's, from the drawer;
 * renaming, recolouring and retiring are the admin's Settings page, gated on {@code WORKSPACE_MANAGE}.
 */
@RestController
@RequestMapping("/api/v1/candidate-tags")
@RequiredArgsConstructor
public class CandidateTagController {

    private final CandidateTagService tags;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public List<CandidateTagResponse> catalog(@AuthenticationPrincipal AuthPrincipal principal) {
        return tags.catalog(principal.requireWorkspaceId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireWorkspacePermission(WorkspaceAction.CANDIDATE_POOL_MANAGE)
    public CandidateTagResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody CreateCandidateTagRequest request,
                                       HttpServletRequest httpRequest) {
        return tags.create(principal.userId(), principal.requireWorkspaceId(), request, httpRequest);
    }

    @PatchMapping("/{tagId}")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public CandidateTagResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID tagId,
                                       @Valid @RequestBody UpdateCandidateTagRequest request,
                                       HttpServletRequest httpRequest) {
        return tags.update(principal.userId(), principal.requireWorkspaceId(), tagId, request, httpRequest);
    }
}

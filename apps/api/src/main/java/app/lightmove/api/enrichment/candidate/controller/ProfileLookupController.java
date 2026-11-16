package app.lightmove.api.enrichment.candidate.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.candidate.dto.ProfileLookupRequest;
import app.lightmove.api.enrichment.candidate.dto.ProfileLookupResponse;
import app.lightmove.api.enrichment.candidate.service.ProfileLookupService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The Add form's "paste a LinkedIn profile" read. WORK_EXECUTE though it writes nothing: it spends a vendor read. */
@RestController
@RequiredArgsConstructor
public class ProfileLookupController {

    private final ProfileLookupService lookups;

    @PostMapping("/api/v1/projects/{projectId}/candidates/profile-lookup")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ProfileLookupResponse lookUp(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @Valid @RequestBody ProfileLookupRequest request) {
        return lookups.lookUp(principal.userId(), principal.requireWorkspaceId(), projectId, request.linkedinUrl());
    }
}

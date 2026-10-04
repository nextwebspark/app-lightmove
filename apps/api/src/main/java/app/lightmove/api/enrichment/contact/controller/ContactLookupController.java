package app.lightmove.api.enrichment.contact.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.enrichment.contact.dto.ContactLookupResponse;
import app.lightmove.api.enrichment.contact.service.ContactLookupService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Find email and Find phone buttons — two endpoints because the channels spend from separate
 * pools. WORK_EXECUTE though it reads a person: it writes the row and spends money.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates/{candidateId}/contact")
@RequiredArgsConstructor
public class ContactLookupController {

    private final ContactLookupService lookups;

    @PostMapping("/email")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ContactLookupResponse findEmail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            HttpServletRequest httpRequest) {
        return lookups.findEmail(principal.userId(), principal.requireWorkspaceId(),
                projectId, candidateId, httpRequest);
    }

    @PostMapping("/phone")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ContactLookupResponse findPhone(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            HttpServletRequest httpRequest) {
        return lookups.findPhone(principal.userId(), principal.requireWorkspaceId(),
                projectId, candidateId, httpRequest);
    }
}

package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.ErasePersonRequest;
import app.lightmove.api.candidate.dto.PersonErasurePreviewResponse;
import app.lightmove.api.candidate.service.PersonErasureService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

/** Erasing a person for good, an admin's alone ({@code PERSON_ERASE}): what it deletes, then the deletion. */
@RestController
@RequestMapping("/api/v1/candidates/{personId}")
@RequiredArgsConstructor
public class PersonErasureController {

    private final PersonErasureService erasure;

    @GetMapping("/erase-preview")
    @RequireWorkspacePermission(WorkspaceAction.PERSON_ERASE)
    public PersonErasurePreviewResponse preview(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable UUID personId) {
        return erasure.preview(principal.requireWorkspaceId(), personId);
    }

    @PostMapping("/erase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireWorkspacePermission(WorkspaceAction.PERSON_ERASE)
    public void erase(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID personId,
                      @Valid @RequestBody ErasePersonRequest request, HttpServletRequest httpRequest) {
        erasure.erase(principal.userId(), principal.requireWorkspaceId(), personId, request, httpRequest);
    }
}

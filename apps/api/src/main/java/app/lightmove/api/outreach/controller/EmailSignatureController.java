package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.EmailSignatureResponse;
import app.lightmove.api.outreach.dto.EmailSignaturesResponse;
import app.lightmove.api.outreach.dto.RenderedSignatureResponse;
import app.lightmove.api.outreach.dto.SaveSignatureRequest;
import app.lightmove.api.outreach.service.EmailSignatureService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings → Email signature: the caller's own signatures in the session's workspace. Staff only — a pure client sends
 * no email — and nothing else to authorise: every row read or written is the caller's, keyed on the principal.
 */
@RestController
@RequiredArgsConstructor
public class EmailSignatureController {

    private final EmailSignatureService signatures;

    @GetMapping("/api/v1/me/signatures")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailSignaturesResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return signatures.list(principal.userId(), principal.requireWorkspaceId());
    }

    @PostMapping("/api/v1/me/signatures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailSignatureResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody SaveSignatureRequest request) {
        return signatures.create(principal.userId(), principal.requireWorkspaceId(), request);
    }

    @PutMapping("/api/v1/me/signatures/{signatureId}")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailSignatureResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID signatureId,
                                         @Valid @RequestBody SaveSignatureRequest request) {
        return signatures.update(principal.userId(), principal.requireWorkspaceId(), signatureId, request);
    }

    @PostMapping("/api/v1/me/signatures/{signatureId}/default")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public EmailSignatureResponse makeDefault(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable UUID signatureId) {
        return signatures.makeDefault(principal.userId(), principal.requireWorkspaceId(), signatureId);
    }

    @DeleteMapping("/api/v1/me/signatures/{signatureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID signatureId) {
        signatures.delete(principal.userId(), principal.requireWorkspaceId(), signatureId);
    }

    /** Draws what the editor holds without saving it, so the preview is the server's own rendering. */
    @PostMapping("/api/v1/me/signatures/preview")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public RenderedSignatureResponse preview(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody SaveSignatureRequest request) {
        return signatures.preview(principal.userId(), principal.requireWorkspaceId(), request);
    }
}

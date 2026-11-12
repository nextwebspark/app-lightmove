package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.EmailSignatureResponse;
import app.lightmove.api.outreach.dto.EmailSignaturesResponse;
import app.lightmove.api.outreach.dto.RenderedSignatureResponse;
import app.lightmove.api.outreach.dto.SaveSignatureRequest;
import app.lightmove.api.outreach.dto.SignaturePictureResponse;
import app.lightmove.api.outreach.dto.SignatureStarterResponse;
import app.lightmove.api.outreach.dto.SignatureTestResponse;
import app.lightmove.api.outreach.service.EmailSignatureService;
import app.lightmove.api.outreach.service.MailboxService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings → Email signature: the caller's own signatures in the session's workspace. Staff only — a pure client sends
 * no email — and nothing else to authorise: every row read or written is the caller's, keyed on the principal.
 */
@RestController
@RequiredArgsConstructor
public class EmailSignatureController {

    /** The {@code EMAIL_IMAGE_TOO_LARGE} sentence's 5 MB. */
    private static final long MAX_PICTURE_BYTES = 5L * 1024 * 1024;

    private final EmailSignatureService signatures;
    private final MailboxService mailboxes;
    private final RateLimitGuard rateLimit;

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

    @PostMapping(path = "/api/v1/me/signatures/pictures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public SignaturePictureResponse uploadPicture(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @RequestParam(name = "file", required = false) MultipartFile file,
                                                  HttpServletRequest httpRequest) {
        rateLimit.checkSignaturePictureUpload(principal.userId(), httpRequest);
        if (file == null || file.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "file", "Choose a picture");
        }
        if (file.getSize() > MAX_PICTURE_BYTES) {
            throw ApiException.of(ErrorCode.EMAIL_IMAGE_TOO_LARGE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException unreadable) {
            throw ApiException.of(ErrorCode.EMAIL_IMAGE_UNREADABLE);
        }
        return signatures.uploadPicture(principal.userId(), principal.requireWorkspaceId(), content);
    }

    /** A starting point, or an older layout, as Markdown the editor goes on with; nothing is saved. */
    @PostMapping("/api/v1/me/signatures/starter")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public SignatureStarterResponse starter(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody SaveSignatureRequest request) {
        return signatures.starterOf(principal.userId(), principal.requireWorkspaceId(), request);
    }

    /** Sends what the editor holds, saved or not, from the caller's mailbox to itself. */
    @PostMapping("/api/v1/me/signatures/test")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public SignatureTestResponse sendTest(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody SaveSignatureRequest request,
                                          HttpServletRequest httpRequest) {
        rateLimit.checkSignatureTestSend(principal.userId(), httpRequest);
        return new SignatureTestResponse(
                mailboxes.sendSignatureTest(principal.userId(), principal.requireWorkspaceId(), request));
    }

    /** Draws what the editor holds without saving it, so the preview is the server's own rendering. */
    @PostMapping("/api/v1/me/signatures/preview")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public RenderedSignatureResponse preview(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody SaveSignatureRequest request) {
        return signatures.preview(principal.userId(), principal.requireWorkspaceId(), request);
    }
}

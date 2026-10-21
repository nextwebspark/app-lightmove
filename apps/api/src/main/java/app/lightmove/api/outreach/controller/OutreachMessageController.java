package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.dto.ScheduledMessageResponse;
import app.lightmove.api.outreach.dto.SendMessageRequest;
import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import app.lightmove.api.outreach.service.OutreachAttachments;
import app.lightmove.api.outreach.service.OutreachMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** A consultant's own email to an executive on a position, sent from their mailbox. {@code WORK_EXECUTE}. */
@RestController
@RequiredArgsConstructor
public class OutreachMessageController {

    private final OutreachMessageService messages;
    private final OutreachAttachments attachments;

    @PostMapping("/api/v1/projects/{projectId}/outreach/conversations/{candidateId}/messages")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledMessageResponse send(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                         @Valid @RequestBody SendMessageRequest request,
                                         HttpServletRequest httpRequest) {
        return messages.schedule(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                List.of(), httpRequest);
    }

    /** The same email carrying files uploaded with it: the request as a {@code message} part, each file a {@code files} part. */
    @PostMapping(path = "/api/v1/projects/{projectId}/outreach/conversations/{candidateId}/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledMessageResponse sendWithFiles(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable UUID projectId, @PathVariable UUID candidateId,
                                                  @Valid @RequestPart("message") SendMessageRequest request,
                                                  @RequestPart(name = "files", required = false)
                                                  List<MultipartFile> files,
                                                  HttpServletRequest httpRequest) {
        return messages.schedule(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId, request,
                files == null ? List.of() : files, httpRequest);
    }

    /** A file an email in the conversation carried, as it went. Every download is recorded. */
    @GetMapping("/api/v1/projects/{projectId}/outreach/messages/{messageId}/attachments/{attachmentId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ResponseEntity<Resource> attachment(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable UUID projectId, @PathVariable UUID messageId,
                                               @PathVariable UUID attachmentId, HttpServletRequest httpRequest) {
        OutreachMessageAttachment file = messages.attachmentOf(principal.userId(), principal.requireWorkspaceId(),
                projectId, messageId, attachmentId, httpRequest);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(attachments.open(file)));
    }

    @DeleteMapping("/api/v1/projects/{projectId}/outreach/messages/{messageId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID projectId,
                       @PathVariable UUID messageId, HttpServletRequest httpRequest) {
        messages.cancel(principal.userId(), principal.requireWorkspaceId(), projectId, messageId, httpRequest);
    }
}

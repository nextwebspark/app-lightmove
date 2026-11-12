package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.model.SubjectAccessArchive;
import app.lightmove.api.candidate.service.PersonSubjectAccessService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.export.service.ZipArchiveWriter;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Everything held on one person as a zip, to answer their subject access request (#919): an admin's alone. */
@RestController
@RequestMapping("/api/v1/candidates/{personId}")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class PersonSubjectAccessController {

    private final PersonSubjectAccessService subjectAccess;

    @GetMapping("/subject-access-export")
    @RequireWorkspacePermission(WorkspaceAction.PERSON_SUBJECT_ACCESS)
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable UUID personId,
                                                        HttpServletRequest httpRequest) {
        SubjectAccessArchive archive = subjectAccess.archiveOf(principal.userId(), principal.requireWorkspaceId(),
                personId, httpRequest);
        String fileName = "uncava-subject-access-" + ZipArchiveWriter.slugOf(archive.personName(), "person") + "-"
                + archive.exportedAt().atOffset(ZoneOffset.UTC).toLocalDate() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(out -> subjectAccess.write(archive, out));
    }
}

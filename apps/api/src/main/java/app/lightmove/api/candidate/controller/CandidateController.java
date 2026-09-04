package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateStatusRequest;
import app.lightmove.api.candidate.model.StoredPhoto;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A mandate's mapped executives. Gated per method exactly as the companies are: reading is WORK_VIEW,
 * held by every seated role including CLIENT, while every write is WORK_EXECUTE — a client
 * representative may see who has been mapped at a company without being able to add, edit or remove
 * anyone.
 *
 * <p>Editing is a PUT rather than a PATCH because the drawer holds every field and submits every field.
 * A partial merge over twenty fields would have to invent a meaning for an omitted one, which makes
 * "cleared" and "not sent" the same request — and clearing a compensation figure is exactly the edit
 * that must not silently do nothing.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidates;

    /**
     * {@code triageCompanyId} is repeatable, and is how the Companies grid reads: it renders one page
     * of companies and asks for the people at exactly those. {@code unmapped} asks for the other side
     * — the executives whose employer is not in the mandate's universe at all. Neither means the whole
     * mandate.
     */
    @GetMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<CandidatesResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable UUID projectId,
                                                   @RequestParam(required = false) List<UUID> triageCompanyId,
                                                   @RequestParam(required = false) Boolean unmapped,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        CandidateListCriteria criteria =
                new CandidateListCriteria(triageCompanyId, unmapped, q, page, size);
        return ResponseEntity.ok(candidates.list(principal.requireWorkspaceId(), projectId, criteria));
    }

    /**
     * The stored profile photo, inline. Safe to serve under its stored type, unlike the position
     * document: these bytes came from enrichment's own server-side download, which accepts raster
     * image formats only — no caller ever uploads them. {@code nosniff} stays on regardless.
     */
    @GetMapping("/{candidateId}/photo")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_VIEW')")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable UUID projectId,
                                        @PathVariable UUID candidateId) {
        StoredPhoto photo = candidates.photoOf(principal.requireWorkspaceId(), projectId, candidateId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(photo.content());
    }

    @PostMapping
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CandidateResponse> add(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable UUID projectId,
                                                 @Valid @RequestBody SaveCandidateRequest request,
                                                 HttpServletRequest httpRequest) {
        CandidateResponse added = candidates.add(principal.userId(), principal.requireWorkspaceId(),
                projectId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @PutMapping("/{candidateId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CandidateResponse> replace(@AuthenticationPrincipal AuthPrincipal principal,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID candidateId,
                                                     @Valid @RequestBody SaveCandidateRequest request,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidates.replace(principal.userId(), principal.requireWorkspaceId(),
                projectId, candidateId, request, httpRequest));
    }

    /**
     * A status change on its own — the pill on the read-only profile panel.
     *
     * <p>A PATCH beside the PUT above because the two are different acts: that one is the drawer's
     * whole form, this is one value flicked while reading. Re-submitting a profile that has been on
     * screen for a while, only to change its status, would overwrite whatever was edited since.
     */
    @PatchMapping("/{candidateId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<CandidateResponse> changeStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody UpdateCandidateStatusRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidates.changeStatus(principal.userId(),
                principal.requireWorkspaceId(), projectId, candidateId, request, httpRequest));
    }

    /** Removes this mandate's research on a person. Another mandate's row about them is untouched. */
    @DeleteMapping("/{candidateId}")
    @PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'WORK_EXECUTE')")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID candidateId,
                                       HttpServletRequest httpRequest) {
        candidates.remove(principal.userId(), principal.requireWorkspaceId(), projectId, candidateId,
                httpRequest);
        return ResponseEntity.noContent().build();
    }
}

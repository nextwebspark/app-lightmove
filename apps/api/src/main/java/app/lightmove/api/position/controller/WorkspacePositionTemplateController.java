package app.lightmove.api.position.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.position.dto.PositionTemplateDetail;
import app.lightmove.api.position.dto.PositionTemplateHiddenRequest;
import app.lightmove.api.position.dto.PositionTemplateOverview;
import app.lightmove.api.position.dto.PositionTemplateWriteRequest;
import app.lightmove.api.position.dto.TemplateImportResponse;
import app.lightmove.api.position.service.WorkspacePositionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Settings → Templates: the caller's firm's role templates. Every call is POSITION_TEMPLATE_MANAGE in
 * the caller's own workspace; the library itself is edited on {@link PositionTemplateLibraryController}.
 */
@RestController
@RequestMapping("/api/v1/workspace/position-templates")
@RequiredArgsConstructor
public class WorkspacePositionTemplateController {

    private static final String TEMPLATES_GATE = "@workspaceAuthorizer.can(principal, 'POSITION_TEMPLATE_MANAGE')";

    private final WorkspacePositionTemplateService templates;

    @GetMapping
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<List<PositionTemplateOverview>> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(templates.list(principal.requireWorkspaceId()));
    }

    @GetMapping("/{code}")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<PositionTemplateDetail> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable String code) {
        return ResponseEntity.ok(templates.get(principal.requireWorkspaceId(), code));
    }

    @PostMapping
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<PositionTemplateDetail> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody PositionTemplateWriteRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templates.create(principal.userId(),
                principal.requireWorkspaceId(), request, httpRequest));
    }

    /** Saving a library template takes the firm's own copy of it. */
    @PutMapping("/{code}")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<PositionTemplateDetail> save(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String code,
                                                       @Valid @RequestBody PositionTemplateWriteRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(templates.save(principal.userId(), principal.requireWorkspaceId(), code,
                request, httpRequest));
    }

    /** Resets the firm's copy to the library's template, or deletes a template the firm wrote. */
    @DeleteMapping("/{code}")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String code, @RequestParam long version,
                                       HttpServletRequest httpRequest) {
        templates.remove(principal.userId(), principal.requireWorkspaceId(), code, version, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{code}/hidden")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<PositionTemplateDetail> setHidden(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable String code,
                                                            @Valid @RequestBody PositionTemplateHiddenRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(templates.setHidden(principal.userId(), principal.requireWorkspaceId(), code,
                request.hidden(), httpRequest));
    }

    @GetMapping("/export")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal principal) {
        return PositionTemplateFileResponse.attachment(templates.export(principal.requireWorkspaceId()),
                MediaType.APPLICATION_JSON, "lightmove-position-templates.json");
    }

    @GetMapping("/schema")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<byte[]> schema() {
        return PositionTemplateFileResponse.attachment(templates.schema(), PositionTemplateFileResponse.SCHEMA,
                PositionTemplateFileResponse.SCHEMA_FILE_NAME);
    }

    @PostMapping("/import/preview")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<TemplateImportResponse> previewImport(@AuthenticationPrincipal AuthPrincipal principal,
                                                                @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(templates.previewImport(principal.requireWorkspaceId(), file));
    }

    @PostMapping("/import/commit")
    @PreAuthorize(TEMPLATES_GATE)
    public ResponseEntity<TemplateImportResponse> commitImport(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @RequestParam("file") MultipartFile file,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(templates.commitImport(principal.userId(), principal.requireWorkspaceId(), file,
                httpRequest));
    }
}

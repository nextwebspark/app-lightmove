package app.lightmove.api.positiontemplate.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.positiontemplate.dto.PositionTemplateDetail;
import app.lightmove.api.positiontemplate.dto.PositionTemplateHiddenRequest;
import app.lightmove.api.positiontemplate.dto.PositionTemplateOverview;
import app.lightmove.api.positiontemplate.dto.PositionTemplateWriteRequest;
import app.lightmove.api.positiontemplate.dto.TemplateImportResponse;
import app.lightmove.api.positiontemplate.service.WorkspacePositionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.ResponseStatus;
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


    private final WorkspacePositionTemplateService templates;

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public List<PositionTemplateOverview> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return templates.list(principal.requireWorkspaceId());
    }

    @GetMapping("/{code}")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public PositionTemplateDetail get(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String code) {
        return templates.get(principal.requireWorkspaceId(), code);
    }

    @PostMapping
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    public PositionTemplateDetail create(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody PositionTemplateWriteRequest request,
                                         HttpServletRequest httpRequest) {
        return templates.create(principal.userId(),
                principal.requireWorkspaceId(), request, httpRequest);
    }

    /** Saving a library template takes the firm's own copy of it. */
    @PutMapping("/{code}")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public PositionTemplateDetail save(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String code,
                                       @Valid @RequestBody PositionTemplateWriteRequest request,
                                       HttpServletRequest httpRequest) {
        return templates.save(principal.userId(), principal.requireWorkspaceId(), code,
                request, httpRequest);
    }

    /** Resets the firm's copy to the library's template, or deletes a template the firm wrote. */
    @DeleteMapping("/{code}")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable String code, @RequestParam long version,
                       HttpServletRequest httpRequest) {
        templates.remove(principal.userId(), principal.requireWorkspaceId(), code, version, httpRequest);
    }

    @PatchMapping("/{code}/hidden")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public PositionTemplateDetail setHidden(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String code,
                                            @Valid @RequestBody PositionTemplateHiddenRequest request,
                                            HttpServletRequest httpRequest) {
        return templates.setHidden(principal.userId(), principal.requireWorkspaceId(), code,
                request.hidden(), httpRequest);
    }

    @GetMapping("/export")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthPrincipal principal) {
        return PositionTemplateFileResponse.attachment(templates.export(principal.requireWorkspaceId()),
                MediaType.APPLICATION_JSON, "lightmove-position-templates.json");
    }

    @GetMapping("/schema")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public ResponseEntity<byte[]> schema() {
        return PositionTemplateFileResponse.attachment(templates.schema(), PositionTemplateFileResponse.SCHEMA,
                PositionTemplateFileResponse.SCHEMA_FILE_NAME);
    }

    @PostMapping("/import/preview")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public TemplateImportResponse previewImport(@AuthenticationPrincipal AuthPrincipal principal,
                                                @RequestParam("file") MultipartFile file) {
        return templates.previewImport(principal.requireWorkspaceId(), file);
    }

    @PostMapping("/import/commit")
    @RequireWorkspacePermission(WorkspaceAction.POSITION_TEMPLATE_MANAGE)
    public TemplateImportResponse commitImport(@AuthenticationPrincipal AuthPrincipal principal,
                                               @RequestParam("file") MultipartFile file,
                                               HttpServletRequest httpRequest) {
        return templates.commitImport(principal.userId(), principal.requireWorkspaceId(), file,
                httpRequest);
    }
}

package app.lightmove.api.positiontemplate.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.positiontemplate.dto.PositionTemplateActiveRequest;
import app.lightmove.api.positiontemplate.dto.PositionTemplateDetail;
import app.lightmove.api.positiontemplate.dto.PositionTemplateOverview;
import app.lightmove.api.positiontemplate.dto.PositionTemplateWriteRequest;
import app.lightmove.api.positiontemplate.dto.TemplateImportResponse;
import app.lightmove.api.positiontemplate.service.PositionTemplateLibraryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * The LightMove role-template library, for a platform super admin. Gated on the platform action alone:
 * nothing here reads or writes inside a workspace, so nothing here asks which one the caller is in.
 */
@RestController
@RequestMapping("/api/v1/platform/position-templates")
@RequiredArgsConstructor
public class PositionTemplateLibraryController {

    private static final String LIBRARY_GATE = "@platformAuthorizer.can(principal, 'TEMPLATE_LIBRARY_MANAGE')";

    private final PositionTemplateLibraryService library;

    @GetMapping
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<List<PositionTemplateOverview>> list() {
        return ResponseEntity.ok(library.list());
    }

    @GetMapping("/{code}")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<PositionTemplateDetail> get(@PathVariable String code) {
        return ResponseEntity.ok(library.get(code));
    }

    @PostMapping
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<PositionTemplateDetail> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @Valid @RequestBody PositionTemplateWriteRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(library.create(principal.userId(), request, httpRequest));
    }

    @PutMapping("/{code}")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<PositionTemplateDetail> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                         @PathVariable String code,
                                                         @Valid @RequestBody PositionTemplateWriteRequest request,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(library.update(principal.userId(), code, request, httpRequest));
    }

    @PatchMapping("/{code}/active")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<PositionTemplateDetail> setActive(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @PathVariable String code,
                                                            @Valid @RequestBody PositionTemplateActiveRequest request,
                                                            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(library.setActive(principal.userId(), code, request.active(), httpRequest));
    }

    @GetMapping("/export")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<byte[]> export() {
        return PositionTemplateFileResponse.attachment(library.export(), MediaType.APPLICATION_JSON,
                "lightmove-template-library.json");
    }

    @GetMapping("/schema")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<byte[]> schema() {
        return PositionTemplateFileResponse.attachment(library.schema(), PositionTemplateFileResponse.SCHEMA,
                PositionTemplateFileResponse.SCHEMA_FILE_NAME);
    }

    /** Reads the file and answers with what an import would do. Writes nothing. */
    @PostMapping("/import/preview")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<TemplateImportResponse> previewImport(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(library.previewImport(file));
    }

    /** Re-reads the same file and writes it, all or nothing. */
    @PostMapping("/import/commit")
    @PreAuthorize(LIBRARY_GATE)
    public ResponseEntity<TemplateImportResponse> commitImport(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @RequestParam("file") MultipartFile file,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(library.commitImport(principal.userId(), file, httpRequest));
    }
}

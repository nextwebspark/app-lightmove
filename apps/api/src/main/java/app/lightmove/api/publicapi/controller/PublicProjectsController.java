package app.lightmove.api.publicapi.controller;

import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicReader;
import app.lightmove.api.core.security.apikey.RequirePublicProjectRead;
import app.lightmove.api.core.security.apikey.RequirePublicScope;
import app.lightmove.api.publicapi.dto.PublicCandidate;
import app.lightmove.api.publicapi.dto.PublicCompany;
import app.lightmove.api.publicapi.dto.PublicPage;
import app.lightmove.api.publicapi.dto.PublicProblem;
import app.lightmove.api.publicapi.dto.PublicProject;
import app.lightmove.api.publicapi.dto.PublicUniverse;
import app.lightmove.api.publicapi.service.PublicApiReadAudit;
import app.lightmove.api.publicapi.service.PublicReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A workspace's positions, their companies and their executives, read-only. */
@RestController
@RequestMapping(path = "/api/v1/public/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@ApiResponse(responseCode = "400",
        description = "A parameter is out of range or not one of its values (VALIDATION_FAILED), or a universe too large "
                + "for one call (PUBLIC_API_UNIVERSE_TOO_LARGE)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
@ApiResponse(responseCode = "401", description = "The key is missing, invalid, revoked or expired (API_KEY_INVALID)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
@ApiResponse(responseCode = "403",
        description = "The key lacks the scope (API_KEY_SCOPE_MISSING), or its owner has no seat on the position (FORBIDDEN)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
@ApiResponse(responseCode = "429", description = "Too many requests (RATE_LIMITED); wait for Retry-After seconds",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
public class PublicProjectsController {

    private static final String NOT_FOUND = "No such position in the key's workspace (NOT_FOUND)";

    private final PublicReadService reads;
    private final PublicApiReadAudit audit;

    @GetMapping
    @RequirePublicScope(ApiKeyScope.PROJECTS_READ)
    @Tag(name = "Projects", description = "Positions: the search and mapping mandates")
    @Operation(operationId = "listProjects", summary = "List positions",
            description = "Needs `projects:read`. A personal key lists the positions its owner can open; "
                    + "a workspace key lists every position. Newest first.")
    @ApiResponse(responseCode = "200", description = "One page of positions")
    public PublicPage<PublicProject> list(
            @Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key,
            @Parameter(description = "Only positions whose title contains this, ignoring case", example = "Finance")
            @RequestParam(required = false) String title,
            @Parameter(description = "Page number, from 0", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Rows per page", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "25"))
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        PublicPage<PublicProject> found = reads.projects(PublicReader.of(key), title, page, size);
        audit.record(key, null, request, found.data().size());
        return found;
    }

    @GetMapping("/{projectId}")
    @RequirePublicProjectRead(ApiKeyScope.PROJECTS_READ)
    @Tag(name = "Projects")
    @Operation(operationId = "getProject", summary = "Get one position", description = "Needs `projects:read`.")
    @ApiResponse(responseCode = "200", description = "The position")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
    public PublicProject get(@Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key,
                             @Parameter(description = "The position's id") @PathVariable UUID projectId,
                             HttpServletRequest request) {
        PublicProject project = reads.project(PublicReader.of(key), projectId);
        audit.record(key, projectId, request, 1);
        return project;
    }

    @GetMapping("/{projectId}/companies")
    @RequirePublicProjectRead(ApiKeyScope.COMPANIES_READ)
    @Tag(name = "Companies", description = "The companies a position has filed, a stage at a time")
    @Operation(operationId = "listCompanies", summary = "List a position's companies",
            description = "Needs `companies:read`. One stage at a time, newest first.")
    @ApiResponse(responseCode = "200", description = "One page of the stage")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
    public PublicPage<PublicCompany> companies(
            @Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key,
            @Parameter(description = "The position's id") @PathVariable UUID projectId,
            @Parameter(description = "The stage to read",
                    schema = @Schema(type = "string", allowableValues = {"inUniverse", "shortlisted", "declined"},
                            defaultValue = "inUniverse"))
            @RequestParam(required = false) String stage,
            @Parameter(description = "Page number, from 0", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Rows per page", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "25"))
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        PublicPage<PublicCompany> found = reads.companies(PublicReader.of(key), projectId, stage, page, size);
        audit.record(key, projectId, request, found.data().size());
        return found;
    }

    @GetMapping("/{projectId}/candidates")
    @RequirePublicProjectRead(ApiKeyScope.CANDIDATES_READ)
    @Tag(name = "Candidates", description = "The executives a position has mapped")
    @Operation(operationId = "listCandidates", summary = "List a position's executives",
            description = "Needs `candidates:read`. `contacts` is filled only with `candidates.contacts:read`, "
                    + "and `compensation` only with `candidates.compensation:read`; otherwise both are null. "
                    + "First mapped first.")
    @ApiResponse(responseCode = "200", description = "One page of executives")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
    public PublicPage<PublicCandidate> candidates(
            @Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key,
            @Parameter(description = "The position's id") @PathVariable UUID projectId,
            @Parameter(description = "Only executives at this status",
                    schema = @Schema(type = "string", allowableValues = {"identified", "contacted", "engaged", "interested",
                            "notInterested", "offLimits", "outOfScope"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "Only executives mapped at this company: a Company's id from the companies route")
            @RequestParam(required = false) UUID companyId,
            @Parameter(description = "Page number, from 0", schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Rows per page", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "25"))
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        PublicPage<PublicCandidate> found = reads.candidates(PublicReader.of(key), projectId, status, companyId, page,
                size);
        audit.record(key, projectId, request, found.data().size());
        return found;
    }

    @GetMapping("/{projectId}/universe")
    @PreAuthorize("@publicApiAuthorizer.canReadProject(principal, #projectId, 'COMPANIES_READ', 'CANDIDATES_READ')")
    @Tag(name = "Universe", description = "A stage's companies with their executives nested, in one call")
    @Operation(operationId = "getUniverse", summary = "Read a stage with its executives",
            description = "Needs `companies:read` and `candidates:read`. Every company of the stage, each with "
                    + "the executives mapped at it, unpaged; on inUniverse, executives at no company come back "
                    + "in `unassigned`. `contacts` and `compensation` follow the same scopes as on the "
                    + "candidates route. A stage too large for one call is refused with "
                    + "PUBLIC_API_UNIVERSE_TOO_LARGE, never cut short; page through the companies and "
                    + "candidates routes instead.")
    @ApiResponse(responseCode = "200", description = "The whole stage")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = PublicProblem.class)))
    public PublicUniverse universe(
            @Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key,
            @Parameter(description = "The position's id") @PathVariable UUID projectId,
            @Parameter(description = "The stage to read",
                    schema = @Schema(type = "string", allowableValues = {"inUniverse", "shortlisted", "declined"},
                            defaultValue = "inUniverse"))
            @RequestParam(required = false) String stage,
            HttpServletRequest request) {
        PublicUniverse universe = reads.universe(PublicReader.of(key), projectId, stage);
        audit.record(key, projectId, request, universe.companies().size() + universe.unassigned().size()
                + universe.companies().stream().mapToInt(company -> company.executives().size()).sum());
        return universe;
    }
}

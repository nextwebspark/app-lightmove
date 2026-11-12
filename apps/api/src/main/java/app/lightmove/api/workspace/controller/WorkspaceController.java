package app.lightmove.api.workspace.controller;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.text.service.SuppliedText;
import app.lightmove.api.workspace.dto.DeleteWorkspaceRequest;
import app.lightmove.api.workspace.dto.InferredBackgroundResponse;
import app.lightmove.api.workspace.dto.UpdateWorkspaceAiControlsRequest;
import app.lightmove.api.workspace.dto.UpdateWorkspaceCalendarSyncRequest;
import app.lightmove.api.workspace.dto.UpdateWorkspaceDateTimeRequest;
import app.lightmove.api.workspace.dto.UpdateWorkspaceModeRequest;
import app.lightmove.api.workspace.dto.UpdateWorkspacePersonaRequest;
import app.lightmove.api.workspace.dto.UpdateWorkspaceSettingsRequest;
import app.lightmove.api.workspace.dto.WorkspaceAiControlsResponse;
import app.lightmove.api.workspace.dto.WorkspaceCompanyResponse;
import app.lightmove.api.workspace.dto.WorkspaceDateTimeResponse;
import app.lightmove.api.workspace.dto.WorkspaceResponse;
import app.lightmove.api.workspace.model.SuggestedDateTime;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.service.WorkspaceSettingsService.WorkspaceDetail;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Settings → General, always the caller's own workspace; changing it is WORKSPACE_MANAGE. */
@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceSettingsService settings;
    private final LightMoveProperties properties;

    @GetMapping
    @PreAuthorize("@workspaceAuthorizer.staff(principal)")
    public WorkspaceResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return toResponse(
                settings.get(principal.requireWorkspaceId()));
    }

    @PatchMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody UpdateWorkspaceSettingsRequest request,
                                    HttpServletRequest httpRequest) {
        return toResponse(settings.update(
                principal.userId(), principal.requireWorkspaceId(),
                request.name(), request.apolloAccountId(), request.defaultRegion(), request.defaultCurrency(),
                httpRequest));
    }

    /** The firm persona the assistant reads. */
    @PutMapping("/persona")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse updatePersona(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody UpdateWorkspacePersonaRequest request,
                                           HttpServletRequest httpRequest) {
        HiringPersona persona = new HiringPersona(request.summary(), request.sectors(),
                request.competitors(), request.geographies(), request.notes());
        return toResponse(settings.updatePersona(
                principal.userId(), principal.requireWorkspaceId(), persona, httpRequest));
    }

    @PutMapping("/mode")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse changeMode(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody UpdateWorkspaceModeRequest request,
                                        HttpServletRequest httpRequest) {
        return toResponse(settings.changeMode(
                principal.userId(), principal.requireWorkspaceId(), request.mode(), httpRequest));
    }

    @PutMapping("/calendar-sync")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse changeCalendarSync(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody UpdateWorkspaceCalendarSyncRequest request,
                                                HttpServletRequest httpRequest) {
        return toResponse(settings.changeCalendarSync(
                principal.userId(), principal.requireWorkspaceId(), request.calendarSync(), httpRequest));
    }

    /** Turning gender or nationality off clears the AI's unconfirmed values for every person, in the same write. */
    @PutMapping("/ai-controls")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse changeAiControls(@AuthenticationPrincipal AuthPrincipal principal,
                                              @Valid @RequestBody UpdateWorkspaceAiControlsRequest request,
                                              HttpServletRequest httpRequest) {
        return toResponse(settings.changeAiControls(
                principal.userId(), principal.requireWorkspaceId(), request.controls(), httpRequest));
    }

    @GetMapping("/ai-controls/inferred-background")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public InferredBackgroundResponse inferredBackground(@AuthenticationPrincipal AuthPrincipal principal) {
        return InferredBackgroundResponse.of(settings.inferredBackgroundOf(principal.requireWorkspaceId()));
    }

    @PutMapping("/date-time")
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    public WorkspaceResponse changeDateTime(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody UpdateWorkspaceDateTimeRequest request,
                                            HttpServletRequest httpRequest) {
        return toResponse(settings.changeDateTime(principal.userId(), principal.requireWorkspaceId(),
                SuppliedText.collapseWhitespaceToNull(request.timeZone()), request.dateFormat(), request.timeFormat(),
                httpRequest));
    }

    @DeleteMapping
    @RequireWorkspacePermission(WorkspaceAction.WORKSPACE_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @Valid @RequestBody DeleteWorkspaceRequest request,
                       HttpServletRequest httpRequest) {
        settings.delete(principal.userId(), principal.requireWorkspaceId(),
                request.confirmName(), httpRequest);
    }

    private WorkspaceResponse toResponse(WorkspaceDetail detail) {
        Workspace ws = detail.workspace();
        return new WorkspaceResponse(ws.getId(), ws.getName(), ws.getSlug(), ws.getLogoMark(),
                ws.getEmailDomain(), ws.getMode(), ws.getCalendarSync(),
                WorkspaceAiControlsResponse.of(ws.getAiControls()), ws.getDefaultRegion(),
                ws.getDefaultCurrency(), ws.getPlan(),
                detail.memberCount(), ws.getCreatedAt(), ws.getPersona(),
                WorkspaceCompanyResponse.of(ws.getCompany()), dateTimeOf(detail),
                properties.retention().deletedWorkspacesAfter().toDays());
    }

    private static WorkspaceDateTimeResponse dateTimeOf(WorkspaceDetail detail) {
        Workspace ws = detail.workspace();
        SuggestedDateTime suggested = detail.suggestedDateTime();
        String suggestedFrom = suggested.countryCode() == null ? null : Countries.nameOf(suggested.countryCode());
        return new WorkspaceDateTimeResponse(ws.getTimeZone(), ws.getDateFormat(), ws.getTimeFormat(),
                suggested.preferences().zone().getId(), suggestedFrom, detail.membersAutomaticTimeZone());
    }
}

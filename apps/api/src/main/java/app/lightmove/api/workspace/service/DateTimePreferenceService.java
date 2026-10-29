package app.lightmove.api.workspace.service;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.common.time.service.CountryTimeZones;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How dates and times read, settled one value at a time: the person's own choice, else the workspace's, else the
 * country's zone and the standard formats. The one place that order lives — a mailbox's send window, the digest's
 * hour and every email's dates all ask here.
 */
@Service
@RequiredArgsConstructor
public class DateTimePreferenceService {

    private final WorkspaceRepository workspaces;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public DateTimePreferences effectiveFor(UUID workspaceId, UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return forPerson(user, requireWorkspace(workspaceId));
    }

    /** A person's clock in one workspace: what their mailbox there sends by and their digest from it goes in. */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID workspaceId, UUID userId) {
        return effectiveFor(workspaceId, userId).zone();
    }

    @Transactional(readOnly = true)
    public DateTimePreferences workspaceDefaultsOf(UUID workspaceId) {
        return forWorkspace(requireWorkspace(workspaceId));
    }

    /** @param workspace null outside any workspace, where only the person's own choices and the standard remain */
    public DateTimePreferences forPerson(User user, Workspace workspace) {
        return DateTimePreferences.settle(user.getTimezone(), user.getDateFormat(), user.getTimeFormat(),
                workspace == null ? DateTimePreferences.STANDARD : forWorkspace(workspace));
    }

    public DateTimePreferences forWorkspace(Workspace workspace) {
        return DateTimePreferences.settle(workspace.getTimeZone(), workspace.getDateFormat(), workspace.getTimeFormat(),
                suggestedFor(workspace));
    }

    /** What a workspace reads in until an admin chooses: its country's zone and the one global standard formats. */
    public DateTimePreferences suggestedFor(Workspace workspace) {
        return new DateTimePreferences(
                CountryTimeZones.zoneOf(workspace.getCompanyCountry()).orElse(DateTimePreferences.STANDARD.zone()),
                DateFormatStyle.STANDARD, TimeFormatStyle.STANDARD);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }
}

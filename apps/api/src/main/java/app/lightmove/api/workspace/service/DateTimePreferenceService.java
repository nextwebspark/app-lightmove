package app.lightmove.api.workspace.service;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.common.time.service.CountryTimeZones;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.model.SuggestedDateTime;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place the order lives: the zone a person pinned, else their device's, else the workspace's, else its
 * country's; each format the person's, else the workspace's, else the standard.
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
        return forPerson(user, workspace == null ? DateTimePreferences.STANDARD : forWorkspace(workspace));
    }

    /** For a caller settling many people of one workspace, which reads its defaults once. */
    public DateTimePreferences forPerson(User user, DateTimePreferences workspaceDefaults) {
        String zone = user.getTimezone() != null ? user.getTimezone() : user.getDeviceTimeZone();
        return DateTimePreferences.settle(zone, user.getDateFormat(), user.getTimeFormat(), workspaceDefaults);
    }

    public DateTimePreferences forWorkspace(Workspace workspace) {
        return DateTimePreferences.settle(workspace.getTimeZone(), workspace.getDateFormat(), workspace.getTimeFormat(),
                suggestionOf(workspace).preferences());
    }

    public SuggestedDateTime suggestionOf(Workspace workspace) {
        Optional<ZoneId> countryZone = CountryTimeZones.zoneOf(workspace.getCompanyCountry());
        DateTimePreferences preferences = new DateTimePreferences(
                countryZone.orElse(DateTimePreferences.STANDARD.zone()), DateFormatStyle.STANDARD,
                TimeFormatStyle.STANDARD);
        return new SuggestedDateTime(preferences, countryZone.isPresent() ? workspace.getCompanyCountry() : null);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }
}

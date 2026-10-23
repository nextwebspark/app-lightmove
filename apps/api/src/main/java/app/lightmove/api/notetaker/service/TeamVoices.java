package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which voices on a call are the team's: a named speaker whose name is an active staff member's, or the name of
 * whoever sent the bot. Decided here so no model has to guess from a display name, and so a consultant's own words
 * never count as the executive's evidence.
 */
@Component
@RequiredArgsConstructor
class TeamVoices {

    private final WorkspaceAccess workspaceAccess;
    private final UserRepository users;

    Set<String> namesOf(UUID workspaceId, Collection<UUID> alsoUserIds) {
        List<UUID> userIds = Stream.concat(
                        workspaceAccess.activeStaff(workspaceId).stream().map(WorkspaceMember::getUserId),
                        alsoUserIds.stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return users.findAllById(userIds).stream()
                .map(User::getFullName)
                .map(TeamVoices::nameKey)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    static boolean isTeam(Set<String> team, String speaker, boolean named) {
        return named && team.contains(nameKey(speaker));
    }

    static String nameKey(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

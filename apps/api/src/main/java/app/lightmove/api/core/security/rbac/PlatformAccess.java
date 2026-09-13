package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single answer to "may this user act on what every workspace shares?", re-read from the database
 * on every call. Platform roles are granted outside the application and never ride in the JWT.
 */
@Service
@RequiredArgsConstructor
public class PlatformAccess {

    private final RoleRepository roles;

    /** A miss is a 404 rather than a 403, so the platform surface is not advertised to anyone else. */
    public void requireAction(UUID userId, PlatformAction action) {
        if (!roles.findPlatformActionNames(userId).contains(action.name())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Requires the " + action.name() + " platform action");
        }
    }

    /**
     * Skips a name this build does not know rather than failing on it: a migration seeding a new platform
     * action lands while older instances are still serving, and this runs on every login, refresh and /me.
     */
    public List<PlatformAction> actionsOf(UUID userId) {
        Set<String> granted = Set.copyOf(roles.findPlatformActionNames(userId));
        return Arrays.stream(PlatformAction.values())
                .filter(action -> granted.contains(action.name()))
                .toList();
    }
}

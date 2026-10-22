package app.lightmove.api.core.security.service;

import app.lightmove.api.common.time.service.RegionZones;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.ProfileUpdateCommand;
import app.lightmove.api.core.security.model.TimezoneChanged;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Profile: the caller editing their own display fields.
 *
 * <p>There is no authorisation to declare. The row being written is the actor's own, identified by the
 * authenticated principal rather than by anything in the request, so no action in the RBAC catalog
 * applies — the same reasoning that leaves {@code GET /auth/me} ungated.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /**
     * The languages the picker offers. Held here rather than derived from {@link java.util.Locale},
     * which would accept every tag the JDK knows and let someone store one the product will never
     * translate into.
     */
    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "ar", "fr");

    private final UserRepository users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    @Transactional
    public User update(UUID actorId, UUID workspaceId, ProfileUpdateCommand command,
                       HttpServletRequest request) {
        User user = users.findById(actorId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        String previousTimezone = user.getTimezone();
        String timezone = command.timezone().trim();
        boolean isMoved = !previousTimezone.equals(timezone);
        user.setFullName(command.fullName().trim());
        user.setTitle(normaliseTitle(command.title()));
        user.setTimezone(isMoved ? requireRegionZone(timezone) : timezone);
        user.setLocale(requireSupportedLocale(command.locale()));

        audit.event(AuthEventType.PROFILE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("fullName", user.getFullName())
                .detailIfPresent("timezoneFrom", isMoved ? previousTimezone : null)
                .detailIfPresent("timezoneTo", isMoved ? user.getTimezone() : null)
                .record();
        if (isMoved) {
            events.publishEvent(new TimezoneChanged(actorId, user.zone()));
        }

        return user;
    }

    /** An empty box is "no title", and the column is nullable — storing "" would be a title of no width. */
    private static String normaliseTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    /**
     * Asked of a new choice only: a zone saved before the rule (one the old check took, like {@code US/Pacific}) still
     * saves unchanged with the rest of the profile, and still reads correctly.
     */
    private static String requireRegionZone(String timezone) {
        if (!RegionZones.isRegionZone(timezone)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "timezone",
                    "Pick a timezone from the list");
        }
        return timezone;
    }

    private static String requireSupportedLocale(String locale) {
        String candidate = locale.trim();
        if (!SUPPORTED_LOCALES.contains(candidate)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "locale",
                    "Pick a language from the list");
        }
        return candidate;
    }
}

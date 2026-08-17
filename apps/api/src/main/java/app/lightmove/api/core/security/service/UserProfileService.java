package app.lightmove.api.core.security.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.ProfileUpdateCommand;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public User update(UUID actorId, UUID workspaceId, ProfileUpdateCommand command,
                       HttpServletRequest request) {
        User user = users.findById(actorId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        user.setFullName(command.fullName().trim());
        user.setTitle(normaliseTitle(command.title()));
        user.setTimezone(requireKnownTimezone(command.timezone()));
        user.setLocale(requireSupportedLocale(command.locale()));

        audit.event(AuthEventType.PROFILE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("fullName", user.getFullName())
                .record();

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
     * Checked against the JDK's tz database rather than the four the picker shows, so widening the
     * picker stays a frontend change — and so a value we cannot later format a date in is refused here.
     */
    private static String requireKnownTimezone(String timezone) {
        String candidate = timezone.trim();
        if (!ZoneId.getAvailableZoneIds().contains(candidate)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "timezone",
                    "Pick a timezone from the list");
        }
        return candidate;
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

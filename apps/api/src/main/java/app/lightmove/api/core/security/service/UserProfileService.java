package app.lightmove.api.core.security.service;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.service.StandardTimeZones;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.ProfileUpdateCommand;
import app.lightmove.api.core.security.model.TimezoneChanged;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Objects;
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
    private final Clock clock;

    @Transactional
    public User update(UUID actorId, UUID workspaceId, ProfileUpdateCommand command,
                       HttpServletRequest request) {
        // A deleted account's access token outlives it by up to its fifteen minutes; it must not name the row again.
        User user = users.findById(actorId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));

        String previousTimezone = user.getTimezone();
        String timezone = command.timezone() == null || command.timezone().isBlank() ? null : command.timezone().trim();
        boolean isMoved = !Objects.equals(previousTimezone, timezone);
        DateFormatStyle previousDateFormat = user.getDateFormat();
        TimeFormatStyle previousTimeFormat = user.getTimeFormat();
        user.setFullName(command.fullName().trim());
        user.setTitle(normaliseTitle(command.title()));
        user.setTimezone(isMoved ? requireOfferedZone(timezone) : timezone);
        user.setDateFormat(command.dateFormat());
        user.setTimeFormat(command.timeFormat());
        user.setLocale(requireSupportedLocale(command.locale()));
        if (command.phone() != null) {
            user.setPhone(ContactDetails.phoneOrNull(command.phone()));
        }
        if (command.linkedinUrl() != null) {
            user.setLinkedinUrl(ContactDetails.linkedinProfileOrNull(command.linkedinUrl()));
        }

        boolean isDateFormatMoved = previousDateFormat != user.getDateFormat();
        boolean isTimeFormatMoved = previousTimeFormat != user.getTimeFormat();
        audit.event(AuthEventType.PROFILE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detailIfPresent("timezoneFrom", isMoved ? orAutomatic(previousTimezone) : null)
                .detailIfPresent("timezoneTo", isMoved ? orAutomatic(user.getTimezone()) : null)
                .detailIfPresent("dateFormatFrom", isDateFormatMoved ? orWorkspace(previousDateFormat) : null)
                .detailIfPresent("dateFormatTo", isDateFormatMoved ? orWorkspace(user.getDateFormat()) : null)
                .detailIfPresent("timeFormatFrom", isTimeFormatMoved ? orWorkspace(previousTimeFormat) : null)
                .detailIfPresent("timeFormatTo", isTimeFormatMoved ? orWorkspace(user.getTimeFormat()) : null)
                .record();
        if (isMoved) {
            events.publishEvent(new TimezoneChanged(actorId));
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
     * What this person's browser says its zone is, kept as their clock while they pin none. Unchanged, it writes
     * nothing; a zone no browser can mean is refused. Not audited: it moves with every trip, and a pinned zone, which
     * the profile save audits, is the only choice it stands for.
     */
    @Transactional
    public User reportDeviceTimeZone(UUID actorId, String zone) {
        User user = users.findById(actorId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        String reported = zone == null ? null : zone.trim();
        if (!StandardTimeZones.isRegionZone(reported)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "timeZone", "That is not a time zone");
        }
        if (user.reportDeviceTimeZone(reported, clock.instant()) && user.isTimeZoneAutomatic()) {
            events.publishEvent(new TimezoneChanged(actorId));
        }
        return user;
    }

    /** What the audit line says for a zone left to the device. */
    private static String orAutomatic(Object choice) {
        return choice == null ? "automatic" : choice.toString();
    }

    /** What the audit line says for a choice left to the workspace. */
    private static String orWorkspace(Object choice) {
        return choice == null ? "workspace" : choice.toString();
    }

    /**
     * Asked of a new choice only: a zone saved before the fixed list (one an older check took, like {@code US/Pacific}
     * or {@code Asia/Tashkent}) still saves unchanged with the rest of the profile, and still reads correctly. Going
     * back to Automatic is always allowed.
     */
    private static String requireOfferedZone(String timezone) {
        if (timezone != null && !StandardTimeZones.isOffered(timezone)) {
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

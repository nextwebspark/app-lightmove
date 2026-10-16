package app.uncava.api.notification.service;

import app.uncava.api.common.constant.ApiValueEnum;
import app.uncava.api.core.config.UncavaProperties;
import app.uncava.api.notification.constant.NotificationKind;
import app.uncava.api.notification.constant.NotificationPreferenceKind;
import app.uncava.api.notification.dto.NotificationKindPreferenceDto;
import app.uncava.api.notification.dto.NotificationPreferencesDto;
import app.uncava.api.outreach.service.MailboxService;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Notifications: a person's own choices, per kind and channel, in the session's workspace. Email is
 * offered only for the kinds ever emailed on their own (an assignment, a mention); the rest ride the digest.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final Set<NotificationPreferenceKind> EMAILED = Arrays.stream(NotificationKind.values())
            .filter(NotificationKind::emailable)
            .map(NotificationKind::preference)
            .collect(Collectors.toUnmodifiableSet());

    private final NotificationPreferences preferences;
    private final MailboxService mailboxes;
    private final UncavaProperties properties;

    @Transactional(readOnly = true)
    public NotificationPreferencesDto of(UUID userId, UUID workspaceId) {
        Map<NotificationPreferenceKind, NotificationPreferences.Channels> chosen = preferences.of(userId, workspaceId);
        return new NotificationPreferencesDto(Arrays.stream(NotificationPreferenceKind.values())
                .map(kind -> new NotificationKindPreferenceDto(kind.value(), chosen.get(kind).inApp(),
                        EMAILED.contains(kind) && chosen.get(kind).email()))
                .toList(), preferences.digest(userId, workspaceId), zoneOf(userId, workspaceId).getId());
    }

    @Transactional
    public NotificationPreferencesDto change(UUID userId, UUID workspaceId, NotificationPreferencesDto wanted) {
        for (NotificationKindPreferenceDto row : wanted.kinds()) {
            NotificationPreferenceKind kind = ApiValueEnum.require(NotificationPreferenceKind.class, row.kind(),
                    "notification kind");
            preferences.save(userId, workspaceId, kind.name(), row.inApp(), EMAILED.contains(kind) && row.email());
        }
        preferences.save(userId, workspaceId, NotificationPreferences.DIGEST, false, wanted.digest());
        return of(userId, workspaceId);
    }

    /** The zone a person's day is read in: their mailbox's, else the deployment's default. */
    ZoneId zoneOf(UUID userId, UUID workspaceId) {
        return mailboxes.timeZoneOf(userId, workspaceId)
                .orElseGet(() -> ZoneId.of(properties.notifications().defaultTimeZone()));
    }
}

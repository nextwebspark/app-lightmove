package app.lightmove.api.notification.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.notification.constant.NotificationKind;
import app.lightmove.api.notification.constant.NotificationPreferenceKind;
import app.lightmove.api.notification.dto.NotificationKindPreferenceDto;
import app.lightmove.api.notification.dto.NotificationPreferencesDto;
import app.lightmove.api.workspace.service.DateTimePreferenceService;
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
    private final DateTimePreferenceService timezones;

    @Transactional(readOnly = true)
    public NotificationPreferencesDto of(UUID userId, UUID workspaceId) {
        Map<NotificationPreferenceKind, NotificationPreferences.Channels> chosen = preferences.of(userId, workspaceId);
        return new NotificationPreferencesDto(Arrays.stream(NotificationPreferenceKind.values())
                .map(kind -> new NotificationKindPreferenceDto(kind.value(), chosen.get(kind).inApp(),
                        EMAILED.contains(kind) && chosen.get(kind).email()))
                .toList(), preferences.digest(userId, workspaceId), timezones.zoneOf(workspaceId, userId).getId());
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
}

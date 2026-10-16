package app.uncava.api.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Settings → Notifications: each kind × channel, the daily digest, and the zone it goes out in (read-only). */
public record NotificationPreferencesDto(@NotNull @Valid List<NotificationKindPreferenceDto> kinds,
                                         @NotNull Boolean digest, String timeZone) {}

package app.uncava.api.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** One row of Settings → Notifications. */
public record NotificationKindPreferenceDto(@NotBlank String kind, @NotNull Boolean inApp, @NotNull Boolean email) {}

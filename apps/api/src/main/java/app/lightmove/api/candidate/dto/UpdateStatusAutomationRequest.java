package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** An admin's choice for one event: on or off, and the status it moves people to (required to switch it on). */
public record UpdateStatusAutomationRequest(@NotNull Boolean enabled, UUID statusId) {}

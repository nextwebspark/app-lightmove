package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** The teammate a next action is assigned to, as a card draws them: always with their avatar. */
public record NextActionAssigneeDto(UUID userId, String fullName, String avatarUrl) {}

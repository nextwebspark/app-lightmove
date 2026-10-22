package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** Who filed an executive into a position: the firm's own attribution, so a staff read's alone. */
public record AddedByResponse(UUID userId, String name, String avatarUrl) {}

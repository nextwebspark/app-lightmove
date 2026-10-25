package app.lightmove.api.notetaker.dto;

import java.util.UUID;

/** Someone with a copilot window open on the call; {@code you}: the caller. {@code avatarUrl}: their sign-in picture. */
public record CopilotViewerResponse(UUID userId, String name, String avatarUrl, boolean you) {}

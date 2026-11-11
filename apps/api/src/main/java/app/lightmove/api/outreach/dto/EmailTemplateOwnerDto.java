package app.lightmove.api.outreach.dto;

import java.util.UUID;

/** Who an email template belongs to, as its row and the picker draw them. */
public record EmailTemplateOwnerDto(UUID userId, String fullName, String avatarUrl) {}

package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientRepStatus;
import java.util.UUID;

/**
 * A client-side contact on this mandate: seated with a CLIENT seat (ACTIVE) or attached while
 * their portal invitation is still outstanding (INVITED — they are seated automatically on accept).
 */
public record AttachedRepresentativeResponse(
        UUID representativeId,
        String fullName,
        String position,
        String email,
        ClientRepStatus status
) {}

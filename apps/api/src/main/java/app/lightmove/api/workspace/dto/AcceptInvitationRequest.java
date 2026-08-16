package app.lightmove.api.workspace.dto;

import jakarta.validation.constraints.NotBlank;

/** Token-bearing accept, for the invitee who opened the emailed link with an account already signed in. */
public record AcceptInvitationRequest(
        @NotBlank String token
) {}

package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * How a representative was onboarded: an existing member ({@code memberUserId}) given CLIENT
 * directly, or a stranger sent an {@code invitation}. Exactly one is set.
 */
public record ClientRepresentativeOnboarding(
        boolean existingMember,
        UUID memberUserId,
        Invitation invitation
) {}

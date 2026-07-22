package app.lightmove.api.workspace.model;

import java.util.UUID;

/**
 * The outcome of onboarding a client representative, handed back to the project side so it can create
 * its representative row without knowing how membership was granted.
 *
 * <p>Exactly one of the two paths is taken: an <b>existing member</b> (carrying {@code memberUserId},
 * no invitation) was given the CLIENT role directly, or a <b>stranger</b> (carrying {@code invitation},
 * no user id) was sent the invitation flow. A record of primitives keeps {@code workspace} ignorant of
 * the {@code ClientRepresentative} the project feature owns.
 */
public record ClientRepresentativeOnboarding(
        boolean existingMember,
        UUID memberUserId,
        Invitation invitation
) {}

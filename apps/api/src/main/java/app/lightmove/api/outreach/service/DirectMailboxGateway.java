package app.lightmove.api.outreach.service;

import java.util.List;
import java.util.UUID;

/**
 * Our own gateway at one provider: the provider's API called with an access token from {@link MailboxTokens},
 * through the OAuth app the workspace chose. Its grants are minted by {@code MailboxGrants.mintDirect}.
 */
public interface DirectMailboxGateway extends MailboxGateway {

    /** The provider this gateway connects, in the gateways' shared names ({@code google}, {@code microsoft}). */
    String provider();

    /** Whether {@code workspaceId} has an app to connect through here, shared or its own; Nylas answers otherwise. */
    default boolean isOfferedTo(UUID workspaceId) {
        return isOffered();
    }

    @Override
    default List<String> providers() {
        return List.of(provider());
    }
}

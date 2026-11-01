package app.lightmove.api.core.security.avatar;

import java.util.UUID;

/** A sign-in's provider named a picture it may supply; copied once the sign-in commits. */
public record ProviderAvatarOffered(UUID userId, String source, String pictureUrl) {
}

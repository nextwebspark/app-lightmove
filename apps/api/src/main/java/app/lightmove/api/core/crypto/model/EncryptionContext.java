package app.lightmove.api.core.crypto.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Whose secret a ciphertext is and what it is for. Bound into the ciphertext as associated data, so a value
 * copied onto another owner's row, or into another column, does not decrypt there. The owner is a workspace,
 * or for an account's own secret the user ({@link #ofUser}); the purpose keeps the two apart.
 */
public record EncryptionContext(UUID ownerId, String purpose) {

    public EncryptionContext {
        Objects.requireNonNull(ownerId, "ownerId");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
    }

    public static EncryptionContext ofUser(UUID userId, String purpose) {
        return new EncryptionContext(userId, "user:" + purpose);
    }

    /** Unchanged by the rename from {@code workspaceId}: every ciphertext stored before it still decrypts. */
    public byte[] associatedData() {
        return ("lightmove:" + purpose + ":" + ownerId).getBytes(StandardCharsets.UTF_8);
    }
}

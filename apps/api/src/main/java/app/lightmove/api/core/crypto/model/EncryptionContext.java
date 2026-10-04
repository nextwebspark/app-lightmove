package app.lightmove.api.core.crypto.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Whose secret a ciphertext is and what it is for. Bound into the ciphertext as associated data, so a value
 * copied onto another workspace's row, or into another column, does not decrypt there.
 */
public record EncryptionContext(UUID workspaceId, String purpose) {

    public EncryptionContext {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
    }

    public byte[] associatedData() {
        return ("lightmove:" + purpose + ":" + workspaceId).getBytes(StandardCharsets.UTF_8);
    }
}

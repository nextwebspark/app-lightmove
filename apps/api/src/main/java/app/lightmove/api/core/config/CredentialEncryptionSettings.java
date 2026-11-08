package app.lightmove.api.core.config;

/**
 * The keys the app holds — {@code lightmove.crypto.*}, each mounted from Secret Manager in a deployment.
 * {@code keyset} is a Tink JSON keyset of AES-256-GCM keys, the key-encryption key; blank, the app stores nothing
 * encrypted. {@code suppressionKeyset} is a Tink JSON keyset of HMAC-SHA-256 PRF keys, which the suppression list's
 * hashes are taken under; blank, no workspace can add to its list.
 */
public record CredentialEncryptionSettings(String keyset, String suppressionKeyset) {

    public boolean isConfigured() {
        return keyset != null && !keyset.isBlank();
    }

    public boolean isSuppressionKeysetConfigured() {
        return suppressionKeyset != null && !suppressionKeyset.isBlank();
    }

    /** Either keyset unlocks what it guards, so neither reaches a log line or a bind-failure message. */
    @Override
    public String toString() {
        return "CredentialEncryptionSettings[keyset=<redacted>, suppressionKeyset=<redacted>]";
    }
}

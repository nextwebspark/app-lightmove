package app.uncava.api.core.config;

/**
 * The key-encryption key — {@code uncava.crypto.*}. {@code keyset} is a Tink JSON keyset of AES-256-GCM keys,
 * mounted from Secret Manager in a deployment. Blank, the app boots and stores nothing encrypted.
 */
public record CredentialEncryptionSettings(String keyset) {

    public boolean isConfigured() {
        return keyset != null && !keyset.isBlank();
    }

    /** The keyset decrypts every stored secret, so it never reaches a log line or a bind-failure message. */
    @Override
    public String toString() {
        return "CredentialEncryptionSettings[keyset=<redacted>]";
    }
}

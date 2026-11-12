package app.lightmove.api.core.security.dto;

/** The secret, once: for the QR code the browser draws from {@code otpauthUri}, or typed in as a setup key. */
public record MfaSetupResponse(String secret, String otpauthUri) {

    @Override
    public String toString() {
        return "MfaSetupResponse[secret=<redacted>, otpauthUri=<redacted>]";
    }
}

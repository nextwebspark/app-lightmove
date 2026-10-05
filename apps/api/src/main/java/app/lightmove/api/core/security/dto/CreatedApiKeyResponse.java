package app.lightmove.api.core.security.dto;

/** The one response that carries a key's secret; nothing returns it again. */
public record CreatedApiKeyResponse(ApiKeyResponse key, String secret) {

    @Override
    public String toString() {
        return "CreatedApiKeyResponse[key=" + key + ", secret=<redacted>]";
    }
}

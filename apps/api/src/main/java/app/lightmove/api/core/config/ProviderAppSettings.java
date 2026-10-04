package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Uncava's own OAuth app at one provider, which every workspace on the shared mode connects through. A blank
 * client id or secret is a deployment that does not offer the shared app; a workspace can still bring its own.
 *
 * <p>{@code scopes} is what the app must be granted, shown to an admin registering their own app.
 * {@code ownAppGuideUrl} and {@code sharedAppGuideUrl} are the admin setup guides; blank, no link is drawn.
 */
public record ProviderAppSettings(
        String clientId,
        String clientSecret,
        @DefaultValue List<String> scopes,
        String ownAppGuideUrl,
        String sharedAppGuideUrl
) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        return "ProviderAppSettings[clientId=" + clientId + ", clientSecret=<redacted>, scopes=" + scopes
                + ", ownAppGuideUrl=" + ownAppGuideUrl + ", sharedAppGuideUrl=" + sharedAppGuideUrl + "]";
    }
}

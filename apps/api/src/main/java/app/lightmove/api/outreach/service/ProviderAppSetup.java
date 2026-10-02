package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.config.ProviderAppSettings;
import app.lightmove.api.core.config.ProviderAppsSettings;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * What an admin needs to register a provider's app, and Uncava's own app where this deployment has one: the
 * redirect URI, the scopes, the setup guides and Microsoft's admin-consent link. One place, so the page that
 * shows them and the gateways that send them never disagree.
 */
@Service
@RequiredArgsConstructor
public class ProviderAppSetup {

    static final String MAILBOX_CALLBACK_PATH = "/api/v1/outreach/mailbox/callback";
    static final String ZOOM_CALLBACK_PATH = "/api/v1/outreach/zoom/callback";
    static final String MICROSOFT_ADMIN_CONSENT = "https://login.microsoftonline.com/organizations/adminconsent";
    static final String ADMIN_CONSENT_RETURN_PATH = "/settings/integrations";

    private final LightMoveProperties properties;

    /** Uncava's app at this provider, when this deployment has one. */
    public Optional<ProviderAppSettings> sharedApp(IntegrationProvider provider) {
        return Optional.ofNullable(settingsOf(provider)).filter(ProviderAppSettings::isConfigured);
    }

    public URI redirectUri(IntegrationProvider provider) {
        String path = provider == IntegrationProvider.ZOOM ? ZOOM_CALLBACK_PATH : MAILBOX_CALLBACK_PATH;
        return URI.create(properties.web().baseUrl() + path);
    }

    public List<String> scopes(IntegrationProvider provider) {
        ProviderAppSettings settings = settingsOf(provider);
        return settings == null ? List.of() : List.copyOf(settings.scopes());
    }

    public Optional<String> ownAppGuideUrl(IntegrationProvider provider) {
        return Optional.ofNullable(settingsOf(provider)).map(ProviderAppSettings::ownAppGuideUrl)
                .filter(url -> !url.isBlank());
    }

    public Optional<String> sharedAppGuideUrl(IntegrationProvider provider) {
        return Optional.ofNullable(settingsOf(provider)).map(ProviderAppSettings::sharedAppGuideUrl)
                .filter(url -> !url.isBlank());
    }

    /**
     * The link a Microsoft customer's IT department opens once to approve Uncava's multi-tenant app for the whole
     * organisation, after which every consultant connects without a consent screen of their own.
     */
    public Optional<URI> microsoftAdminConsentUri() {
        return sharedApp(IntegrationProvider.MICROSOFT).map(app -> UriComponentsBuilder
                .fromUriString(MICROSOFT_ADMIN_CONSENT)
                .queryParam("client_id", app.clientId())
                .queryParam("redirect_uri", properties.web().baseUrl() + ADMIN_CONSENT_RETURN_PATH)
                .encode()
                .build()
                .toUri());
    }

    private ProviderAppSettings settingsOf(IntegrationProvider provider) {
        OutreachSettings outreach = properties.outreach();
        ProviderAppsSettings apps = outreach == null ? null : outreach.providers();
        if (apps == null) {
            return null;
        }
        return switch (provider) {
            case GOOGLE -> apps.google();
            case MICROSOFT -> apps.microsoft();
            case ZOOM -> apps.zoom();
        };
    }
}

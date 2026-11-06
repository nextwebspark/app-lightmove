package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.CredentialMode;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.repository.WorkspaceMailIntegrationRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * What a refresh learns about a workspace's own app, for Settings → Integrations. Failing to keep it never changes what
 * the refresh answers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OwnAppKeyHealth {

    private final WorkspaceMailIntegrationRepository integrations;
    private final Clock clock;

    public void refusedAtRefresh(UUID workspaceId, ProviderCredentials app, ProviderAppUnavailable refusal) {
        if (app.mode() != CredentialMode.OWN) {
            return;
        }
        try {
            integrations.recordAppRefused(workspaceId, app.provider(), CredentialMode.OWN, app.clientId(),
                    clock.instant(), AppKeysVerdict.refused(refusal.getMessage()).refusedWith());
        } catch (DataAccessException unrecorded) {
            log.warn("Could not record that workspace {}'s {} app was refused", workspaceId, app.provider(),
                    unrecorded);
        }
    }

    /**
     * Written only while the row says the app was refused, so an ordinary refresh costs nothing. A refresh the
     * provider answers proves the keys, whichever check refused them before.
     */
    public void answeredAtRefresh(UUID workspaceId, ProviderCredentials app) {
        if (app.mode() != CredentialMode.OWN || !app.keysRefused()) {
            return;
        }
        try {
            integrations.clearAppRefused(workspaceId, app.provider(), CredentialMode.OWN, app.clientId(),
                    clock.instant());
        } catch (DataAccessException unrecorded) {
            log.warn("Could not record that workspace {}'s {} app was answered", workspaceId, app.provider(),
                    unrecorded);
        }
    }
}

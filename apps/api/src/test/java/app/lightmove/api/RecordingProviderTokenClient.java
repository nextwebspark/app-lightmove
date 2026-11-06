package app.lightmove.api;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.service.ProviderAppUnavailable;
import app.lightmove.api.outreach.service.ProviderGrantRefused;
import app.lightmove.api.outreach.service.ProviderTokenClient;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Providers' token endpoints, answered locally: no integration test ever reaches Google or Microsoft. */
public class RecordingProviderTokenClient implements ProviderTokenClient {

    private final List<String> revoked = new CopyOnWriteArrayList<>();
    private final List<String> refreshed = new CopyOnWriteArrayList<>();
    private final Set<String> refused = ConcurrentHashMap.newKeySet();
    private final Map<String, String> refusedApps = new ConcurrentHashMap<>();
    private final Set<String> unanswering = ConcurrentHashMap.newKeySet();
    private final List<ProviderCredentials> checked = new CopyOnWriteArrayList<>();

    @Override
    public ProviderTokenGrant refresh(ProviderCredentials credentials, String refreshToken) {
        refreshed.add(refreshToken);
        if (refusedApps.containsKey(credentials.clientId())) {
            throw new ProviderAppUnavailable(refusedApps.get(credentials.clientId()));
        }
        if (refused.contains(refreshToken)) {
            throw new ProviderGrantRefused("invalid_grant");
        }
        return new ProviderTokenGrant("access-for-" + refreshToken, Duration.ofHours(1), null);
    }

    @Override
    public void revoke(IntegrationProvider provider, String token) {
        revoked.add(provider + ":" + token);
    }

    @Override
    public ProviderTokenGrant redeemCode(ProviderCredentials credentials, String code, URI redirectUri,
                                         List<String> scopes) {
        return new ProviderTokenGrant("access-for-" + code, Duration.ofHours(1), "refresh-for-" + code);
    }

    @Override
    public AppKeysVerdict checkKeys(ProviderCredentials credentials, URI redirectUri) {
        checked.add(credentials);
        if (unanswering.contains(credentials.clientId())) {
            throw new VendorException(VendorCall.of("test-oauth", "check-keys"), VendorFailureKind.UNAVAILABLE, null);
        }
        String refusal = refusedApps.get(credentials.clientId());
        return refusal == null ? AppKeysVerdict.ACCEPTED : AppKeysVerdict.refused(refusal);
    }

    /** Every revoke, as {@code PROVIDER:token}. */
    public List<String> revoked() {
        return List.copyOf(revoked);
    }

    /** Every refresh token offered to a token endpoint, refused or not. */
    public List<String> refreshedTokens() {
        return List.copyOf(refreshed);
    }

    /** Every app whose keys were checked, secret included. */
    public List<ProviderCredentials> checkedKeys() {
        return List.copyOf(checked);
    }

    /** The provider will never honour this refresh token again, as after a password change or a removed app. */
    public void refuse(String refreshToken) {
        refused.add(refreshToken);
    }

    /** The provider refuses this app itself, at a key check and at every refresh, answering {@code named}. */
    public void refuseApp(String clientId, String named) {
        refusedApps.put(clientId, named);
    }

    /** The provider answers nothing about this app's keys, as when its endpoint is down. */
    public void answerNothingFor(String clientId) {
        unanswering.add(clientId);
    }

    public void clear() {
        revoked.clear();
        refreshed.clear();
        refused.clear();
        refusedApps.clear();
        unanswering.clear();
        checked.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public RecordingProviderTokenClient recordingProviderTokenClient() {
            return new RecordingProviderTokenClient();
        }
    }
}

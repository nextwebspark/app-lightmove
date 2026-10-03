package app.lightmove.api;

import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.ProviderCredentials;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import app.lightmove.api.outreach.service.ProviderTokenClient;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Providers' token endpoints, answered locally: no integration test ever reaches Google or Microsoft. */
public class RecordingProviderTokenClient implements ProviderTokenClient {

    private final List<String> revoked = new CopyOnWriteArrayList<>();

    @Override
    public ProviderTokenGrant refresh(ProviderCredentials credentials, String refreshToken) {
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

    /** Every revoke, as {@code PROVIDER:token}. */
    public List<String> revoked() {
        return List.copyOf(revoked);
    }

    public void clear() {
        revoked.clear();
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

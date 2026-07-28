package app.lightmove.api.company.config;

import app.lightmove.api.company.model.CoreSignalCompanyRecord;
import app.lightmove.api.company.model.CoreSignalSearchCriteria;
import app.lightmove.api.company.model.CoreSignalSearchResult;
import app.lightmove.api.company.service.CoreSignalClient;
import app.lightmove.api.company.service.CoreSignalGateway;
import app.lightmove.api.company.service.CoreSignalQueryBuilder;
import app.lightmove.api.company.service.CoreSignalUnavailableException;
import app.lightmove.api.core.config.LightMoveProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the {@link CoreSignalGateway} — the one place that knows whether CoreSignal is configured.
 *
 * <p>A blank API key does not fail startup, unlike the email config's fail-fast: email's throw is
 * conditional on <i>choosing</i> the provider, and CoreSignal has no provider switch — an
 * unconditional throw would leave every fresh clone and the whole test suite unbootable. Instead
 * the keyless gateway fails at first use with an honest error a sourcing run surfaces to the UI,
 * which is the same "refuses loudly, not silently" property the email throw buys.
 */
@Configuration
@Slf4j
public class CoreSignalConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    CoreSignalGateway coreSignalGateway(LightMoveProperties properties,
                                        CoreSignalQueryBuilder queryBuilder, ObjectMapper json) {
        LightMoveProperties.CoreSignal config = properties.coresignal();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            log.warn("CoreSignal API key not set (CORESIGNAL_API_KEY) — sourcing runs will fail until it is.");
            return new UnconfiguredCoreSignalGateway();
        }
        log.info("CoreSignal sourcing configured against {}", config.baseUrl());
        return new CoreSignalClient(config.apiKey(), config.baseUrl(),
                RestClient.builder().requestFactory(timeoutBoundFactory()), queryBuilder, json);
    }

    /**
     * Timeouts are the point (same reasoning as {@code EmailSenderConfig}): a hung provider must
     * not pin a sourcing run's thread for as long as the socket takes to give up.
     */
    private static JdkClientHttpRequestFactory timeoutBoundFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(CoreSignalClient.READ_TIMEOUT);
        return factory;
    }

    /** Every call fails with the same honest story: there is no key. Fatal — retrying cannot help. */
    private static final class UnconfiguredCoreSignalGateway implements CoreSignalGateway {

        private static final String DETAIL = "CoreSignal API key not configured (CORESIGNAL_API_KEY)";

        @Override
        public CoreSignalSearchResult searchCompanyIds(CoreSignalSearchCriteria criteria, int limit) {
            throw new CoreSignalUnavailableException(DETAIL, true);
        }

        @Override
        public Optional<CoreSignalCompanyRecord> collect(long coresignalId) {
            throw new CoreSignalUnavailableException(DETAIL, true);
        }
    }
}

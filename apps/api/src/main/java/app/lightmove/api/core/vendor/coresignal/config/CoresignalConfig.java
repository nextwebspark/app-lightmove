package app.lightmove.api.core.vendor.coresignal.config;

import app.lightmove.api.core.config.CoresignalSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.VendorSettings;
import app.lightmove.api.core.vendor.coresignal.service.CoresignalEmployeeClient;
import app.lightmove.api.core.vendor.coresignal.service.CoresignalEmployeeSearch;
import app.lightmove.api.core.vendor.service.VendorCallGuard;
import app.lightmove.api.core.vendor.service.VendorClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires Coresignal, and only when someone has said to.
 *
 * <p>Off unless {@code lightmove.vendor.coresignal.enabled} is true, so a fresh clone and the whole
 * test suite boot with no account, no key and no cost — the same bargain {@code EmailSenderConfig}
 * makes for mail. With it on and no key, {@code VendorClientFactory} refuses at startup rather than
 * at the first sourcing run.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lightmove.vendor.coresignal", name = "enabled", havingValue = "true")
@Slf4j
public class CoresignalConfig {

    @Bean
    CoresignalEmployeeClient coresignalEmployeeClient(LightMoveProperties properties,
                                                      VendorClientFactory clientFactory,
                                                      VendorCallGuard guard,
                                                      RestClient.Builder builder) {
        VendorSettings vendor = properties.vendor();
        CoresignalSettings config = vendor.coresignal();
        log.info("Coresignal is enabled at {}, paced to {} req/s", config.baseUrl(), config.requestsPerSecond());
        return new CoresignalEmployeeClient(config, vendor, clientFactory, guard, builder);
    }

    @Bean
    CoresignalEmployeeSearch coresignalEmployeeSearch(CoresignalEmployeeClient client) {
        return new CoresignalEmployeeSearch(client);
    }
}

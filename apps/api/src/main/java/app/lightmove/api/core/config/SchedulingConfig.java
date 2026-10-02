package app.lightmove.api.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}. Off in tests, which drive a scheduled job by calling it, so a timer
 * firing mid-test never races what the test arranged.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "lightmove.scheduling", name = "enabled", matchIfMissing = true)
public class SchedulingConfig {
}

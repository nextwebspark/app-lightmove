package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(LightMoveProperties.class)
@EnableScheduling // Stranded assistant turns are reclaimed on a timer; see AssistantTurnSweeper.
@EnableAsync // Audit events are written off the request thread; see AuditService.
@EnableResilientMethods // Vendor calls retry through @Retryable; see core/resilience.
public class LightMoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightMoveApplication.class, args);
    }
}

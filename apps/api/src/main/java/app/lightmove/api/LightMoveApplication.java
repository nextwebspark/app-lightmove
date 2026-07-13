package app.lightmove.api;

import app.lightmove.api.common.config.LightMoveProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(LightMoveProperties.class)
@EnableAsync // Audit events are written off the request thread; see AuditService.
public class LightMoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightMoveApplication.class, args);
    }
}

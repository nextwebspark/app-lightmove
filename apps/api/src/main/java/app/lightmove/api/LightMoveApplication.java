package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

// Spring AI would publish every ToolCallback bean in the app as an MCP tool; only @McpTool beans are served.
@SpringBootApplication(exclude = StatelessToolCallbackConverterAutoConfiguration.class)
@EnableConfigurationProperties(LightMoveProperties.class)
@EnableAsync // Audit events are written off the request thread; see AuditService.
@EnableResilientMethods // Vendor calls retry through @Retryable; see core/resilience.
public class LightMoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightMoveApplication.class, args);
    }
}

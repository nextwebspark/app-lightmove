package app.lightmove.api;

import app.lightmove.api.core.config.LightMoveProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.StatelessServerSpecificationFactoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

// Spring AI would publish every ToolCallback bean in the app as an MCP tool; only @McpTool beans are served, and those
// through McpServerConfig's guarded list rather than Spring AI's own.
@SpringBootApplication(exclude = {StatelessToolCallbackConverterAutoConfiguration.class,
        StatelessServerSpecificationFactoryAutoConfiguration.class})
@EnableConfigurationProperties(LightMoveProperties.class)
@EnableAsync // Audit events are written off the request thread; see AuditService.
@EnableResilientMethods // Vendor calls retry through @Retryable; see core/resilience.
public class LightMoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightMoveApplication.class, args);
    }
}

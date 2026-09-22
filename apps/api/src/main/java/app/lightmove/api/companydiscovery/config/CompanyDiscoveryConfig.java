package app.lightmove.api.companydiscovery.config;

import app.lightmove.api.companydiscovery.service.CompanyDiscovery;
import app.lightmove.api.companydiscovery.service.GeminiCompanyDiscovery;
import app.lightmove.api.companydiscovery.service.OffCompanyDiscovery;
import app.lightmove.api.core.config.CompanyDiscoverySettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.service.LlmCallPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Picks the {@link CompanyDiscovery} from config — a yml block, never a branch on a provider name.
 *
 * <p>The adapter is its own {@code @Bean} with {@code defaultCandidate = false}, the shape
 * {@code CompanyEnrichmentConfig} uses. One of that idiom's two reasons applies here and the other
 * does not, which is worth saying plainly: injecting the port would be ambiguous with an ordinary
 * second candidate in the context, so the qualifier is load-bearing; but there is no
 * {@code @Retryable} on this adapter to be made inert, because Spring AI retries the Vertex call
 * under {@code spring.ai.retry} rather than us.
 */
@Configuration
@Slf4j
public class CompanyDiscoveryConfig {

    @Bean(defaultCandidate = false)
    GeminiCompanyDiscovery geminiCompanyDiscovery(
            ChatClient chatClient,
            LightMoveProperties properties,
            @Value("classpath:prompts/company-discovery-system.st") Resource groundedPrompt,
            @Value("classpath:prompts/company-discovery-prose-system.st") Resource prosePrompt,
            @Value("classpath:prompts/company-discovery-extract-system.st") Resource extractPrompt,
            @Value("classpath:prompts/company-discovery-schema.json") Resource answerSchema,
            LlmCallPolicy llmCalls) {
        CompanyDiscoverySettings settings = properties.company().discovery();
        if (!settings.enabled()) {
            return null;
        }
        return new GeminiCompanyDiscovery(chatClient, settings, groundedPrompt, prosePrompt,
                extractPrompt, answerSchema, llmCalls);
    }

    @Bean
    CompanyDiscovery companyDiscovery(
            @Autowired(required = false) @Qualifier("geminiCompanyDiscovery")
            GeminiCompanyDiscovery grounded) {
        if (grounded == null) {
            log.info("AI Research is off — Strategy searches the universe and nothing else.");
            return new OffCompanyDiscovery();
        }
        log.info("AI Research discovers companies through Gemini with Google Search grounding");
        return grounded;
    }
}

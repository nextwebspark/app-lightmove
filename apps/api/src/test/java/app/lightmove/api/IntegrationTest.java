package app.lightmove.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots the whole application against a real Postgres 16 in a throwaway container.
 *
 * <p><b>Postgres, not H2.</b> The schema is hand-written Postgres SQL — partial unique indexes, jsonb,
 * CHECK constraints, an append-only trigger — none of which H2 implements faithfully. A test suite
 * green against H2 would prove the code works on a database we do not ship. It would also never have
 * caught the {@code citext} mismatch, or the {@code inet} cast failure, both of which were real and
 * both of which were Postgres being Postgres.
 *
 * <p>One container for the whole JVM (see {@link TestcontainersConfig}) and, as far as possible, one
 * Spring context: the three recording doubles are imported here rather than per class, because every
 * distinct {@code @Import} combination is another context to boot. A class that needs its own
 * properties ({@code @TestPropertySource}) still pays for one, so there are four, not one. Nothing here
 * touches Cloud SQL, and no test needs a database reset: the container is created fresh, migrated by
 * Flyway, and thrown away.
 *
 * <p>Audit writes run inline here rather than on their own thread — see {@link SynchronousAuditWrites}
 * for why a test that reads {@code app_lm_audit_event} otherwise races the writer.
 *
 * <p>{@code application-test.yml} turns off the real Google GenAI auto-configuration (no GCP
 * credentials in CI), so {@link StubChatModel} and {@link StubEmbeddingModel} stand in — otherwise
 * {@code ChatClientConfig} and the {@code llm} services have no {@code ChatModel}/{@code
 * EmbeddingModel} to wire and the whole context fails to load.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ImportTestcontainers(TestcontainersConfig.class)
@Import({SynchronousAuditWrites.class, StubChatModel.Config.class, StubEmbeddingModel.Config.class,
        RecordingEmailSender.Config.class, RecordingProfileEnricher.Config.class,
        RecordingCompanyEnricher.Config.class})
public @interface IntegrationTest {
}

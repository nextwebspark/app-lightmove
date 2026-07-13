package app.lightmove.api;

import org.springframework.boot.test.context.SpringBootTest;
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
 * <p>The container is started once and reused across every test class (see
 * {@link TestcontainersConfig}), so the whole suite pays for one Postgres, not one per class. Nothing
 * here touches Cloud SQL, and no test needs a database reset: the container is created fresh, migrated
 * by Flyway, and thrown away.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {
}

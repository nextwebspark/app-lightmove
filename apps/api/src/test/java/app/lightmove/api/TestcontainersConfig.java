package app.lightmove.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
// Testcontainers 2.x: org.testcontainers.containers.PostgreSQLContainer is deprecated in favour of
// this one. The old package still resolves, and using it would compile with a warning that quietly
// becomes a breakage on the next major.
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Postgres container every integration test shares.
 *
 * <p>Pinned to 16 because that is what Cloud SQL runs. A test suite passing on a different major
 * version is evidence about a database we do not deploy.
 *
 * <p>The application and Flyway connect as {@code lm_app}, a login that is <b>not</b> a superuser and owns the
 * database — production's shape until #256. A superuser skips every row-level security policy, forced or not, so
 * a suite run as the container's own user would pass with RLS doing nothing. The container's superuser is left to
 * the tests' own fixture SQL ({@link FixtureJdbc}). Wired by property rather than {@code @ServiceConnection}, whose
 * connection details would name the superuser.
 *
 * <p>A static field pulled in through {@code @ImportTestcontainers}, not a {@code @Bean}: Spring starts
 * a static container once per JVM and never stops it, so every cached context — there are four, one
 * per property variant — shares one Postgres. As a bean it was started again for each context, and
 * {@code withReuse(true)} did not save CI, which has no {@code testcontainers.reuse.enable} opt-in:
 * eight containers and eight Flyway runs per build.
 */
interface TestcontainersConfig {

    String APP_ROLE = "lm_app";
    String APP_PASSWORD = "lm_app";

    PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("testcontainers/app-role.sql")
            // Still on: a developer who has opted in keeps the container between runs.
            .withReuse(true);

    @DynamicPropertySource
    static void applicationConnectsAsTheAppRole(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
    }
}

package app.lightmove.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
 * <p>{@code @ServiceConnection} wires the JDBC url, user and password into Spring automatically, so no
 * test has to know a port number. Flyway then applies {@code db/migration/*.sql} into it — the same
 * hand-written SQL that builds production — which means the migrations are genuinely exercised on
 * every run rather than merely assumed to work.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                // Reused across test classes rather than started per class. Spring caches the context,
                // and a singleton container keeps the suite to one Postgres startup instead of a dozen.
                .withReuse(true);
    }
}

package app.lightmove.api;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 *
 * <p>A static field pulled in through {@code @ImportTestcontainers}, not a {@code @Bean}: Spring starts
 * a static container once per JVM and never stops it, so every cached context — there are four, one
 * per property variant — shares one Postgres. As a bean it was started again for each context, and
 * {@code withReuse(true)} did not save CI, which has no {@code testcontainers.reuse.enable} opt-in:
 * eight containers and eight Flyway runs per build.
 */
interface TestcontainersConfig {

    @ServiceConnection
    PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            // Still on: a developer who has opted in keeps the container between runs.
            .withReuse(true);
}

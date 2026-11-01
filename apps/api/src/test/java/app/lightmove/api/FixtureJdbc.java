package app.lightmove.api;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A test's own SQL — the rows it seeds and the rows it checks — runs as the container's superuser, above row-level
 * security: it is not the system under test, and a fixture row of a workspace must not need that workspace's
 * scope to be written or read. The application never sees this template; {@code defaultCandidate = false} keeps it
 * out of every injection point that does not ask for it by name, and out of Boot's own {@code JdbcTemplate}
 * condition.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface FixtureJdbc {

    @TestConfiguration(proxyBeanMethods = false)
    class Config {

        @Bean(defaultCandidate = false)
        @FixtureJdbc
        JdbcTemplate fixtureJdbcTemplate() {
            HikariDataSource superuser = new HikariDataSource();
            superuser.setJdbcUrl(TestcontainersConfig.POSTGRES.getJdbcUrl());
            superuser.setUsername(TestcontainersConfig.POSTGRES.getUsername());
            superuser.setPassword(TestcontainersConfig.POSTGRES.getPassword());
            superuser.setMaximumPoolSize(4);
            superuser.setPoolName("fixture-superuser");
            return new JdbcTemplate(superuser);
        }
    }
}

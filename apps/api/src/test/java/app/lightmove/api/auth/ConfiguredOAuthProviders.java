package app.lightmove.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

/**
 * Google and LinkedIn configured the way production configures them, for the suites that sign in
 * through a provider. One annotation rather than a property list per class so those suites share a
 * single Spring context: identical properties are one cache key, near-identical ones are two boots.
 *
 * <p>Deliberately not part of {@code application-test.yml}: with a registration present,
 * {@code SecurityConfig} wires {@code oauth2Login}, and every other suite must keep proving the
 * no-provider shape.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.linkedin.client-id=test-linkedin-id",
        "spring.security.oauth2.client.registration.linkedin.client-secret=test-linkedin-secret",
        "spring.security.oauth2.client.registration.linkedin.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.linkedin.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.linkedin.client-authentication-method=client_secret_post",
        "spring.security.oauth2.client.registration.linkedin.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "lightmove.auth.oauth.pkce-unsupported-registrations=linkedin",
        "lightmove.auth.oauth.nonce-unsupported-registrations=linkedin",
        "lightmove.auth.oauth.email-verified-optional-registrations=linkedin",
})
@interface ConfiguredOAuthProviders {
}

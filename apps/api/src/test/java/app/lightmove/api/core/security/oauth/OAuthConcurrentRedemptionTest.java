package app.lightmove.api.core.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import tools.jackson.databind.JsonNode;

/** One refresh token or one code is redeemed once, however many requests race for it. */
@IntegrationTest
class OAuthConcurrentRedemptionTest extends OAuthFlowSupport {

    private static final OAuth2TokenType CODE = new OAuth2TokenType("code");

    @Autowired HashingAuthorizationService authorizations;
    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("of two requests holding one refresh token, the second to save is refused and the first's token stands")
    void refreshRedeemedOnce() throws Exception {
        String refreshToken = connect(registerClient(), adminOf(domain), "projects:read")
                .get("refresh_token").asText();
        OAuth2Authorization first = authorizations.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
        OAuth2Authorization second = authorizations.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);

        String winner = Tokens.generate();
        authorizations.save(withRefreshToken(first, winner));

        assertThatThrownBy(() -> authorizations.save(withRefreshToken(second, Tokens.generate())))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("changed");
        assertThat(db.queryForObject("select refresh_token_hash from app_lm_oauth_authorization where id = ?::uuid",
                String.class, first.getId())).isEqualTo(Tokens.hash(winner));
    }

    @Test
    @DisplayName("of two exchanges of one code, the second to save is refused")
    void codeRedeemedOnce() throws Exception {
        String code = authorize(registerClient(), Tokens.generate(), adminOf(domain), "projects:read");
        OAuth2Authorization first = authorizations.findByToken(code, CODE);
        OAuth2Authorization second = authorizations.findByToken(code, CODE);

        authorizations.save(withAccessToken(first));

        assertThatThrownBy(() -> authorizations.save(withAccessToken(second)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("a grant revoked while a request held it is not written back")
    void revokedGrantNotRevived() throws Exception {
        String refreshToken = connect(registerClient(), adminOf(domain), "projects:read")
                .get("refresh_token").asText();
        OAuth2Authorization held = authorizations.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
        db.update("delete from app_lm_oauth_authorization where id = ?::uuid", held.getId());

        assertThatThrownBy(() -> authorizations.save(withRefreshToken(held, Tokens.generate())))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(db.queryForObject("select count(*) from app_lm_oauth_authorization where id = ?::uuid",
                Integer.class, held.getId())).isZero();
    }

    @Test
    @DisplayName("two refreshes of one token sent at once: exactly one gets new tokens")
    void parallelRefreshes() throws Exception {
        String clientId = registerClient();
        String refreshToken = connect(clientId, adminOf(domain), "projects:read").get("refresh_token").asText();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> outcomes = List.of(
                    pool.submit(() -> {
                        start.await();
                        return refresh(clientId, refreshToken).getResponse().getStatus();
                    }),
                    pool.submit(() -> {
                        start.await();
                        return refresh(clientId, refreshToken).getResponse().getStatus();
                    }));
            start.countDown();
            List<Integer> statuses = List.of(outcomes.get(0).get(), outcomes.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            pool.shutdownNow();
        }
    }

    private static OAuth2Authorization withRefreshToken(OAuth2Authorization authorization, String value) {
        Instant now = Instant.now();
        return OAuth2Authorization.from(authorization)
                .refreshToken(new OAuth2RefreshToken(value, now, now.plus(Duration.ofDays(1))))
                .build();
    }

    private static OAuth2Authorization withAccessToken(OAuth2Authorization authorization) {
        Instant now = Instant.now();
        return OAuth2Authorization.from(authorization)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, Tokens.generate(), now,
                        now.plus(Duration.ofHours(1))))
                .build();
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Keys Firm");
        return login(alok);
    }
}

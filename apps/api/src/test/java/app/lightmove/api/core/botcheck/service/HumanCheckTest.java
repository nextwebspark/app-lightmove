package app.lightmove.api.core.botcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.lightmove.api.core.botcheck.constant.HumanCheckAction;
import app.lightmove.api.core.config.AuthSettings;
import app.lightmove.api.core.config.HumanCheckSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.TestAuthSettings;
import app.lightmove.api.core.security.service.ClientIpResolver;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

class HumanCheckTest {

    private static final String VERIFY_URL = "https://challenges.cloudflare.test/siteverify";
    private static final String TOKEN = "solved-token";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer cloudflare = MockRestServiceServer.bindTo(builder).build();

    @Test
    @DisplayName("switched off, it asks nobody and offers the SPA no site key")
    void offAsksNobody() {
        HumanCheck check = checkWith(settings(false, true));

        assertThatCode(() -> check.require(HumanCheckAction.LOGIN, new MockHttpServletRequest()))
                .doesNotThrowAnyException();
        assertThat(check.siteKey()).isNull();
        cloudflare.verify();
    }

    @Test
    @DisplayName("on, a request with no token is refused without asking Cloudflare")
    void refusesAMissingToken() {
        HumanCheck check = checkWith(settings(true, true));

        assertRefused(() -> check.require(HumanCheckAction.LOGIN, new MockHttpServletRequest()));
        assertThat(check.siteKey()).isEqualTo("site-key");
        cloudflare.verify();
    }

    @Test
    @DisplayName("a token solved on this host for this form passes, sent with the secret and the client's address")
    void passesAMatchingAnswer() {
        cloudflare.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("secret=secret-key"),
                        containsString("response=" + TOKEN),
                        containsString("remoteip=203.0.113.9"))))
                .andRespond(answer(true, "beta.uncava.com", "login"));

        assertThatCode(() -> checkWith(settings(true, true)).require(HumanCheckAction.LOGIN, requestWithToken()))
                .doesNotThrowAnyException();
        cloudflare.verify();
    }

    @Test
    @DisplayName("a token solved on another form is refused, so it cannot be spent twice across forms")
    void refusesAnotherFormsToken() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(answer(true, "beta.uncava.com", "signup"));

        assertRefused(() -> checkWith(settings(true, true)).require(HumanCheckAction.LOGIN, requestWithToken()));
    }

    @Test
    @DisplayName("a token solved on another host is refused")
    void refusesAnotherHostsToken() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(answer(true, "evil.example", "login"));

        assertRefused(() -> checkWith(settings(true, true)).require(HumanCheckAction.LOGIN, requestWithToken()));
    }

    @Test
    @DisplayName("with the binding off, as for Cloudflare's test keys, any successful answer passes")
    void bindingOffPassesTestKeyAnswers() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(answer(true, "example.com", ""));

        assertThatCode(() -> checkWith(settings(true, false)).require(HumanCheckAction.SSO, requestWithToken()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a token Cloudflare rejects is refused")
    void refusesARejectedToken() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}", MediaType.APPLICATION_JSON));

        assertRefused(() -> checkWith(settings(true, true)).require(HumanCheckAction.SIGNUP, requestWithToken()));
    }

    @Test
    @DisplayName("Cloudflare's own error lets the request through rather than signing nobody in")
    void cloudflareInternalErrorFailsOpen() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"internal-error\"]}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> checkWith(settings(true, true)).require(HumanCheckAction.LOGIN, requestWithToken()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an outage or a timeout lets the request through")
    void outageFailsOpen() {
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
        cloudflare.expect(requestTo(VERIFY_URL)).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });
        HumanCheck check = checkWith(settings(true, true));

        assertThatCode(() -> check.require(HumanCheckAction.LOGIN, requestWithToken())).doesNotThrowAnyException();
        assertThatCode(() -> check.require(HumanCheckAction.LOGIN, requestWithToken())).doesNotThrowAnyException();
        cloudflare.verify();
    }

    @Test
    @DisplayName("switched on with no secret, it refuses to boot")
    void refusesToBootWithoutASecret() {
        HumanCheckSettings noSecret = new HumanCheckSettings(
                true, "site-key", " ", VERIFY_URL, Duration.ofSeconds(3), true);

        assertThatThrownBy(() -> checkWith(noSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lightmove.auth.human-check");
    }

    private HumanCheck checkWith(HumanCheckSettings humanCheck) {
        LightMoveProperties properties = propertiesWith(humanCheck);
        return new HumanCheck(properties, new ClientIpResolver(properties), builder);
    }

    private static HumanCheckSettings settings(boolean enabled, boolean verifyHostnameAndAction) {
        return new HumanCheckSettings(
                enabled, "site-key", "secret-key", VERIFY_URL, Duration.ofSeconds(3), verifyHostnameAndAction);
    }

    private static LightMoveProperties propertiesWith(HumanCheckSettings humanCheck) {
        AuthSettings base = TestAuthSettings.production().auth();
        AuthSettings auth = new AuthSettings(base.jwt(), base.cookie(), base.lockout(), base.rateLimit(),
                base.extension(), base.mfa(), humanCheck, base.accessTokenTtl(), base.refreshTokenTtl(),
                base.verificationTokenTtl(), base.passwordResetTokenTtl(), base.invitationTtl(),
                base.oauthRequestTtl(), base.requireVerifiedEmail(), base.autoVerifyEmail(), base.bcryptStrength(),
                base.oauth());
        WebSettings web = new WebSettings("https://beta.uncava.com", List.of("https://beta.uncava.com"),
                "/auth/callback", 0, true, DataSize.ofMegabytes(2));
        return new LightMoveProperties(auth, null, web, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader(HumanCheck.HEADER, TOKEN);
        return request;
    }

    private static ResponseCreator answer(boolean success, String hostname, String action) {
        return withSuccess("{\"success\":%s,\"hostname\":\"%s\",\"action\":\"%s\",\"error-codes\":[]}"
                .formatted(success, hostname, action), MediaType.APPLICATION_JSON);
    }

    private static void assertRefused(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class,
                        refusal -> assertThat(refusal.getCode()).isEqualTo(ErrorCode.HUMAN_CHECK_FAILED));
    }
}

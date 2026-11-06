package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.constant.IntegrationProvider;
import app.lightmove.api.outreach.model.AppKeysVerdict;
import app.lightmove.api.outreach.model.ProviderTokenGrant;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/** A token endpoint's answers: a refusal is final, anything else a failure a later try may get past. */
class OAuthProviderTokenClientTest {

    private static final VendorCall CALL = VendorCall.of("google-oauth", "refresh-token");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a dead grant, a refused app and anything else are told apart")
    void refusalsAreToldFromFailures() throws Exception {
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\",\"error_description\":\"x\"}"))
                .isInstanceOf(ProviderGrantRefused.class);
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "{\"error\":\"interaction_required\"}"))
                .isInstanceOf(ProviderGrantRefused.class);
        assertThat(refusalOf(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}"))
                .isInstanceOf(ProviderAppUnavailable.class);
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "{\"error\":\"unauthorized_client\"}"))
                .isInstanceOf(ProviderAppUnavailable.class);
        assertThat(refusalOf(HttpStatus.UNAUTHORIZED, "")).isInstanceOf(ProviderAppUnavailable.class);
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "<html>proxy</html>"))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.BAD_REQUEST));
    }

    @Test
    @DisplayName("a refusal names Entra's rule by number and never quotes its description")
    void aRefusalNamesEntrasRule() throws Exception {
        String body = "{\"error\":\"%s\",\"error_description\":\"AADSTS%d: for jane@retailco.example\","
                + "\"error_codes\":[%d]}";

        assertThat(refusalOf(HttpStatus.BAD_REQUEST, body.formatted("invalid_client", 7000215, 7000215)))
                .isInstanceOf(ProviderAppUnavailable.class)
                .hasMessage("invalid_client AADSTS7000215");
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, body.formatted("invalid_request", 9002313, 9002313)))
                .isInstanceOf(VendorException.class)
                .hasMessage("google-oauth/refresh-token failed: BAD_REQUEST (invalid_request AADSTS9002313)")
                .hasMessageNotContaining("jane@retailco.example");
    }

    @Test
    @DisplayName("an answer reads as the access token, its lifetime and any rotated refresh token")
    void anAnswerIsRead() {
        ProviderTokenGrant token = OAuthProviderTokenClient.read(CALL, JSON.readTree("""
                {"access_token":"ya29.a","expires_in":3599,"refresh_token":"1//new","token_type":"Bearer"}"""));

        assertThat(token.accessToken()).isEqualTo("ya29.a");
        assertThat(token.expiresIn()).isEqualTo(Duration.ofSeconds(3599));
        assertThat(token.refreshToken()).isEqualTo("1//new");
        assertThat(token.toString()).doesNotContain("ya29.a").doesNotContain("1//new");
        assertThatThrownBy(() -> OAuthProviderTokenClient.read(CALL, JSON.readTree("{\"token_type\":\"Bearer\"}")))
                .isInstanceOf(VendorException.class);
    }

    @Test
    @DisplayName("the scopes granted are read off the answer, repeated or spaced however the provider sends them")
    void theGrantedScopesAreRead() {
        ProviderTokenGrant token = OAuthProviderTokenClient.read(CALL, JSON.readTree("""
                {"access_token":"ya29.a","scope":" openid  https://www.googleapis.com/auth/gmail.readonly openid"}"""));
        ProviderTokenGrant unsaid = OAuthProviderTokenClient.read(CALL, JSON.readTree("""
                {"access_token":"ya29.a"}"""));

        assertThat(token.scopes()).containsExactlyInAnyOrder("openid", "https://www.googleapis.com/auth/gmail.readonly");
        assertThat(unsaid.scopes()).isEmpty();
    }

    @Test
    @DisplayName("Google and Zoom refusing a code nobody issued have accepted the keys; refusing the client has not")
    void aCheckAtGoogleOrZoomReadsTheClientRefusal() throws Exception {
        assertThat(verdictOf(IntegrationProvider.GOOGLE, HttpStatus.BAD_REQUEST,
                "{\"error\": \"invalid_grant\", \"error_description\": \"Malformed auth code.\"}"))
                .isEqualTo(AppKeysVerdict.ACCEPTED);
        assertThat(verdictOf(IntegrationProvider.GOOGLE, HttpStatus.BAD_REQUEST,
                "{\"error\": \"redirect_uri_mismatch\", \"error_description\": \"Bad Request\"}"))
                .isEqualTo(AppKeysVerdict.ACCEPTED);
        assertThat(verdictOf(IntegrationProvider.ZOOM, HttpStatus.BAD_REQUEST,
                "{\"reason\":\"Invalid authorization code uncava-key-check\",\"error\":\"invalid_request\"}"))
                .isEqualTo(AppKeysVerdict.ACCEPTED);
        assertThat(verdictOf(IntegrationProvider.GOOGLE, HttpStatus.UNAUTHORIZED,
                "{\"error\": \"invalid_client\", \"error_description\": \"The OAuth client was not found.\"}"))
                .isEqualTo(new AppKeysVerdict("invalid_client"));
        assertThat(verdictOf(IntegrationProvider.GOOGLE, HttpStatus.UNAUTHORIZED,
                "{\"error\": \"invalid_client\", \"error_description\": \"Unauthorized\"}"))
                .isEqualTo(new AppKeysVerdict("invalid_client"));
        assertThat(verdictOf(IntegrationProvider.ZOOM, HttpStatus.UNAUTHORIZED,
                "{\"reason\":\"Invalid client_id or client_secret\",\"error\":\"invalid_client\"}"))
                .isEqualTo(new AppKeysVerdict("invalid_client"));
        assertThat(verdictOf(IntegrationProvider.ZOOM, HttpStatus.UNAUTHORIZED, "")).isEqualTo(new AppKeysVerdict("401"));
    }

    @Test
    @DisplayName("Entra refuses keys for a wrong, expired or unknown secret or app, or an unknown directory, named by rule")
    void aCheckAtMicrosoftNamesTheRule() throws Exception {
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.OK, "{\"access_token\":\"eyJ0\"}"))
                .isEqualTo(AppKeysVerdict.ACCEPTED);
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.UNAUTHORIZED, entra("invalid_client", 7000215)))
                .isEqualTo(new AppKeysVerdict("AADSTS7000215"));
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.UNAUTHORIZED, entra("invalid_client", 7000222)))
                .isEqualTo(new AppKeysVerdict("AADSTS7000222"));
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.BAD_REQUEST,
                entra("unauthorized_client", 700016)))
                .isEqualTo(new AppKeysVerdict("AADSTS700016"));
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.BAD_REQUEST, entra("invalid_request", 90002)))
                .isEqualTo(new AppKeysVerdict("AADSTS90002"));
        assertThat(verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.BAD_REQUEST, entra("invalid_request", 900023)))
                .isEqualTo(new AppKeysVerdict("AADSTS900023"));
    }

    @Test
    @DisplayName("any other Entra refusal says nothing about the keys, so it is no verdict")
    void anotherEntraRefusalIsNoVerdict() {
        assertThatThrownBy(() -> verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.BAD_REQUEST,
                entra("invalid_scope", 1002012)))
                .isInstanceOf(VendorException.class);
        assertThatThrownBy(() -> verdictOf(IntegrationProvider.MICROSOFT, HttpStatus.BAD_REQUEST,
                entra("invalid_request", 90014)))
                .isInstanceOf(VendorException.class);
    }

    @Test
    @DisplayName("a check the provider could not answer is a failure, never a verdict")
    void anUnansweredCheckIsAFailure() {
        assertThatThrownBy(() -> verdictOf(IntegrationProvider.GOOGLE, HttpStatus.SERVICE_UNAVAILABLE, ""))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.UNAVAILABLE));
    }

    private static RuntimeException refusalOf(HttpStatus status, String body) throws Exception {
        return OAuthProviderTokenClient.refusalOrFailure(CALL,
                new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status));
    }

    private static String entra(String error, int rule) {
        return """
                {"error":"%s","error_description":"AADSTS%d: refused. Trace ID: 0b1c Correlation ID: 9f2e",
                 "error_codes":[%d],"timestamp":"2026-11-01 09:00:00Z","trace_id":"0b1c","correlation_id":"9f2e"}"""
                .formatted(error, rule, rule);
    }

    private static AppKeysVerdict verdictOf(IntegrationProvider provider, HttpStatus status, String body)
            throws Exception {
        return OAuthProviderTokenClient.verdictOf(provider, VendorCall.of("oauth", "check-keys"),
                new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status));
    }
}

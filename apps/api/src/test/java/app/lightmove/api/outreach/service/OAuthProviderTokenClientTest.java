package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.RefreshedAccessToken;
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
    @DisplayName("invalid_grant and interaction_required are refusals; other errors are classified by status")
    void refusalsAreToldFromFailures() throws Exception {
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\",\"error_description\":\"x\"}"))
                .isInstanceOf(RefreshTokenRefused.class);
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "{\"error\":\"interaction_required\"}"))
                .isInstanceOf(RefreshTokenRefused.class);
        assertThat(refusalOf(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}"))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.CREDENTIALS));
        assertThat(refusalOf(HttpStatus.BAD_REQUEST, "<html>proxy</html>"))
                .isInstanceOfSatisfying(VendorException.class,
                        failed -> assertThat(failed.getKind()).isEqualTo(VendorFailureKind.BAD_REQUEST));
    }

    @Test
    @DisplayName("an answer reads as the access token, its lifetime and any rotated refresh token")
    void anAnswerIsRead() {
        RefreshedAccessToken token = OAuthProviderTokenClient.read(CALL, JSON.readTree("""
                {"access_token":"ya29.a","expires_in":3599,"refresh_token":"1//new","token_type":"Bearer"}"""));

        assertThat(token.accessToken()).isEqualTo("ya29.a");
        assertThat(token.expiresIn()).isEqualTo(Duration.ofSeconds(3599));
        assertThat(token.rotatedRefreshToken()).isEqualTo("1//new");
        assertThat(token.toString()).doesNotContain("ya29.a").doesNotContain("1//new");
        assertThatThrownBy(() -> OAuthProviderTokenClient.read(CALL, JSON.readTree("{\"token_type\":\"Bearer\"}")))
                .isInstanceOf(VendorException.class);
    }

    private static RuntimeException refusalOf(HttpStatus status, String body) throws Exception {
        return OAuthProviderTokenClient.refusalOrFailure(CALL,
                new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status));
    }
}

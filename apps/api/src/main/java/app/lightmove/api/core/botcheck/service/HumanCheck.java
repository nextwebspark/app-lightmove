package app.lightmove.api.core.botcheck.service;

import app.lightmove.api.core.botcheck.constant.HumanCheckAction;
import app.lightmove.api.core.botcheck.model.SiteverifyAnswer;
import app.lightmove.api.core.config.HumanCheckSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks Cloudflare Turnstile whether an anonymous auth form was sent by a person. Runs before any account is read, so
 * its refusal is the same for every address; an unreachable Cloudflare lets the request through, since the rate limits
 * and the lockout still stand behind it.
 */
@Slf4j
@Component
public class HumanCheck {

    public static final String HEADER = "X-Human-Check";

    /** Turnstile's own ceiling; anything longer was never one of its tokens. */
    private static final int MAX_TOKEN_LENGTH = 2048;

    /** Cloudflare's fault or ours, never the visitor's: refusing on them would sign nobody in. */
    private static final Set<String> NOT_THE_VISITORS_FAULT =
            Set.of("internal-error", "missing-input-secret", "invalid-input-secret");

    private final HumanCheckSettings config;
    private final ClientIpResolver clientIps;
    private final String expectedHostname;
    private final RestClient siteverify;

    @Autowired
    public HumanCheck(LightMoveProperties properties, ClientIpResolver clientIps) {
        this(properties, clientIps,
                RestClient.builder().requestFactory(timeoutBound(properties.auth().humanCheck().timeout())));
    }

    HumanCheck(LightMoveProperties properties, ClientIpResolver clientIps, RestClient.Builder builder) {
        this.config = properties.auth().humanCheck();
        this.clientIps = clientIps;
        String host = URI.create(properties.web().baseUrl().strip()).getHost();
        this.expectedHostname = host == null ? "" : host.toLowerCase(Locale.ROOT);
        this.siteverify = builder.build();
        if (config.enabled() && (isBlank(config.siteKey()) || isBlank(config.secretKey()))) {
            throw new IllegalStateException(
                    "lightmove.auth.human-check.enabled is true but the site key or secret key is blank");
        }
    }

    /** The key the SPA renders the widget with, or null when the check is off and no widget is drawn. */
    public String siteKey() {
        return config.enabled() ? config.siteKey() : null;
    }

    public void require(HumanCheckAction action, HttpServletRequest request) {
        if (!config.enabled()) {
            return;
        }
        String token = request.getHeader(HEADER);
        if (isBlank(token) || token.length() > MAX_TOKEN_LENGTH) {
            throw refusal(action, "no usable token on the request");
        }

        SiteverifyAnswer answer;
        try {
            answer = siteverify.post()
                    .uri(config.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formOf(token, request))
                    .retrieve()
                    .body(SiteverifyAnswer.class);
        } catch (RestClientException outage) {
            log.warn("Human check unreachable on {} ({}); letting the request through",
                    action.code(), outage.getClass().getSimpleName());
            return;
        }
        if (answer == null) {
            log.warn("Human check answered nothing on {}; letting the request through", action.code());
            return;
        }

        if (!answer.success()) {
            if (answer.errorCodesOrNone().stream().anyMatch(NOT_THE_VISITORS_FAULT::contains)) {
                log.error("Human check could not judge {}: {}; letting the request through",
                        action.code(), answer.errorCodesOrNone());
                return;
            }
            throw refusal(action, "refused: " + answer.errorCodesOrNone());
        }
        if (config.verifyHostnameAndAction() && !isForThisSiteAndForm(answer, action)) {
            throw refusal(action, "solved for another host or form");
        }
    }

    private boolean isForThisSiteAndForm(SiteverifyAnswer answer, HumanCheckAction action) {
        return answer.hostname() != null
                && answer.hostname().toLowerCase(Locale.ROOT).equals(expectedHostname)
                && action.code().equals(answer.action());
    }

    private MultiValueMap<String, String> formOf(String token, HttpServletRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", config.secretKey());
        form.add("response", token);
        String clientIp = clientIps.resolve(request);
        if (!"unknown".equals(clientIp)) {
            form.add("remoteip", clientIp);
        }
        return form;
    }

    private static ApiException refusal(HumanCheckAction action, String reason) {
        log.debug("Human check refused {}: {}", action.code(), reason);
        return new ApiException(ErrorCode.HUMAN_CHECK_FAILED, "Human check on " + action.code() + " " + reason);
    }

    private static JdkClientHttpRequestFactory timeoutBound(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

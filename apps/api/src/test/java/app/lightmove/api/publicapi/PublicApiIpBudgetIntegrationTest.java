package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Guessing keys is throttled per caller IP, every attempt counted, so a run of unknown keys reaches no further than the budget. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.public-api.requests-per-minute-per-ip=3")
class PublicApiIpBudgetIntegrationTest {

    private static final String UNKNOWN_KEY = "uncava_pat_notarealkeyatall";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("past the IP's budget a caller gets 429 with Retry-After, whatever key it tries; another IP is untouched")
    void guessingIsThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(me("10.20.30.40")).andExpect(status().isUnauthorized());
        }
        MvcResult throttled = mvc.perform(me("10.20.30.40")).andExpect(status().isTooManyRequests()).andReturn();

        assertThat(throttled.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("20");
        assertThat(throttled.getResponse().getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        mvc.perform(me("10.20.30.41")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder me(String ip) {
        return get("/api/v1/public/me")
                .header("Authorization", "Bearer " + UNKNOWN_KEY)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }
}

package app.lightmove.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The catch-all in {@code GlobalExceptionHandler} must not swallow client mistakes.
 *
 * <p>Anything reaching {@code @ExceptionHandler(Exception.class)} is treated as a bug we did not
 * anticipate: it answers an opaque 500 and logs an ERROR with a stack trace. That is right for a real
 * bug and badly wrong for a wrong URL or a missing parameter — the caller is told "Something went wrong
 * on our end" about a request that was simply malformed, and our own error log fills with other
 * people's typos, burying the one entry that is actually ours.
 *
 * <p>Both cases below were 500s until the SPA moved into this application and made them routine.
 */
@IntegrationTest
class ClientErrorTest {

    @Autowired MockMvc mvc;

    /**
     * A truncated verification link — the one in every signup email — arrives with no token at all.
     * That is a 400, not a 500.
     */
    @Test
    @DisplayName("a missing required parameter is a 400, not a 500")
    void missingParameterIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/verify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /** Now that this application serves a SPA, "no such path" is a normal event, not a server fault. */
    @Test
    @DisplayName("an unresolvable static path is a 404, not a 500")
    void unknownStaticPathIsNotFound() throws Exception {
        mvc.perform(get("/assets/nothing-here.js"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /**
     * Signup is POST-only, and opening its URL in a browser address bar is a GET. The route exists, so
     * this is not a 404 — and nothing on our side broke, so it is certainly not the 500 it used to be.
     */
    @Test
    @DisplayName("the wrong verb on a real route is a 405, not a 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(get("/api/v1/auth/signup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}

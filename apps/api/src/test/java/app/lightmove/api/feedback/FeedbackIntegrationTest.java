package app.lightmove.api.feedback;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The in-app bug reporter's endpoint — the only write in the application a caller with no session may
 * make, which is why it has a test class of its own.
 *
 * <p>No credential is configured in the test profile, so the report is composed and logged rather than
 * filed. Nothing here reaches GitHub.
 */
@IntegrationTest
class FeedbackIntegrationTest {

    @Autowired MockMvc mvc;

    /**
     * The case the whole feature exists for: a tester hits a bug on the login screen, where they have
     * no account to report it from. If this ever starts answering 401, the worst bugs become the ones
     * nobody can tell us about.
     */
    @Test
    @DisplayName("a caller with no session may file a report")
    void acceptsAnAnonymousReport() throws Exception {
        mvc.perform(multipart("/api/v1/feedback")
                        .file(report("""
                                {"kind":"BUG","severity":"HIGH","title":"Login spins forever",
                                 "message":"Pressing sign in leaves the button spinning.",
                                 "reporterEmail":"tester@nextwebspark.com",
                                 "context":{"pageUrl":"/login","viewport":"390x844"}}"""))
                        .file(new MockMultipartFile("screenshot", "screen-capture.png",
                                MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3})))
                .andExpect(status().isCreated())
                // No tracker credential in this profile, so the report was received and logged. That
                // is a success, and the widget says so rather than offering a link to nothing.
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.issueUrl").doesNotExist());
    }

    @Test
    @DisplayName("a report with nothing in it is a 400, not a filed issue")
    void refusesAnEmptyReport() throws Exception {
        mvc.perform(multipart("/api/v1/feedback")
                        .file(report("""
                                {"kind":"BUG","title":"","message":""}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * The declared content type is a claim, and it decides the extension the file would be stored
     * under. An unrecognised one is refused rather than guessed at — this endpoint takes images, not
     * an arbitrary upload channel that happens to be open to strangers.
     */
    @Test
    @DisplayName("a non-image attachment is refused")
    void refusesANonImageAttachment() throws Exception {
        mvc.perform(multipart("/api/v1/feedback")
                        .file(report("""
                                {"kind":"BUG","severity":"LOW","title":"Something is broken",
                                 "message":"The grid does not load at all."}"""))
                        .file(new MockMultipartFile("attachments", "payload.pdf",
                                MediaType.APPLICATION_PDF_VALUE, new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    /**
     * The anonymity is scoped to the POST and nothing else. A GET of the same path is not permitted
     * by name, so it falls through to the rule every other API route lives under and is refused for
     * want of a verified session — which is what keeps "reachable without a session" from quietly
     * becoming "reachable as a navigation".
     */
    @Test
    @DisplayName("only the POST is anonymous; a GET of the same path is refused")
    void onlyThePostIsAnonymous() throws Exception {
        mvc.perform(get("/api/v1/feedback"))
                .andExpect(status().isUnauthorized());
    }

    private static MockMultipartFile report(String json) {
        return new MockMultipartFile("report", "report.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }
}

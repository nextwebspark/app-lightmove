package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Settings → Profile: the caller editing how they appear across the workspace. */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ProfileIntegrationTest extends FlowTestSupport {

    private static final String EDIT = """
            {"fullName":"Alok B Kumar","title":"Managing Partner",
             "timezone":"Asia/Riyadh","locale":"ar"}""";

    @Test
    @DisplayName("a saved profile comes back on the response and on the next /me")
    void savesAndPersists() throws Exception {
        String token = adminOf("Alok Kumar", "Profile Firm");

        mvc.perform(patchProfile(token, EDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alok B Kumar"))
                .andExpect(jsonPath("$.title").value("Managing Partner"))
                .andExpect(jsonPath("$.timezone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.locale").value("ar"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alok B Kumar"))
                .andExpect(jsonPath("$.title").value("Managing Partner"))
                .andExpect(jsonPath("$.timezone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.locale").value("ar"));
    }

    @Test
    @DisplayName("a new account starts on the defaults the screen renders")
    void defaultsAreServed() throws Exception {
        String token = adminOf("Alok Kumar", "Defaults Firm");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Dubai"))
                .andExpect(jsonPath("$.locale").value("en"))
                // The identity row reads "Admin · joined {month} {year}" off this.
                .andExpect(jsonPath("$.workspace.joinedAt").exists());
    }

    @Test
    @DisplayName("an emptied title is no title, not a title of no width")
    void blankTitleBecomesNull() throws Exception {
        String token = adminOf("Alok Kumar", "Titleless Firm");
        mvc.perform(patchProfile(token, EDIT)).andExpect(status().isOk());

        mvc.perform(patchProfile(token, """
                        {"fullName":"Alok Kumar","title":"  ","timezone":"Asia/Dubai","locale":"en"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    @Test
    @DisplayName("a timezone the JDK cannot format a date in is refused, under that field")
    void refusesUnknownTimezone() throws Exception {
        String token = adminOf("Alok Kumar", "Zone Firm");

        MvcResult refused = mvc.perform(patchProfile(token, """
                        {"fullName":"Alok Kumar","title":null,
                         "timezone":"Mars/Olympus","locale":"en"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timezone").exists())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a language the product does not offer is refused, under that field")
    void refusesUnsupportedLocale() throws Exception {
        String token = adminOf("Alok Kumar", "Locale Firm");

        MvcResult refused = mvc.perform(patchProfile(token, """
                        {"fullName":"Alok Kumar","title":null,
                         "timezone":"Asia/Dubai","locale":"zz"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.locale").exists())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a nameless profile is refused — a colleague identifies them by it")
    void refusesBlankName() throws Exception {
        String token = adminOf("Alok Kumar", "Nameless Firm");

        mvc.perform(patchProfile(token, """
                        {"fullName":"   ","title":null,"timezone":"Asia/Dubai","locale":"en"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    @DisplayName("an anonymous caller has no profile to edit")
    void refusesAnonymous() throws Exception {
        mvc.perform(patch("/api/v1/auth/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EDIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the edit lands on the caller, never on a colleague")
    void editsOnlyTheCaller() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Two Person Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(patchProfile(login(sara), """
                        {"fullName":"Sara Mansour","title":"Consultant",
                         "timezone":"Etc/GMT","locale":"fr"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alok Kumar"))
                .andExpect(jsonPath("$.timezone").value("Asia/Dubai"));
    }

    /** A verified admin of a fresh workspace, with tenant claims already in the token. */
    private String adminOf(String name, String workspaceName) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser(name, address), workspaceName);
        return login(address);
    }

    private MockHttpServletRequestBuilder patchProfile(String bearerToken, String payload) {
        return patch("/api/v1/auth/me")
                .header("Authorization", "Bearer " + bearerToken)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }
}

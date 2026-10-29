package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Settings → Profile: the caller editing how they appear across the workspace. */
@IntegrationTest
class ProfileIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

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
                .andExpect(jsonPath("$.workspace.joinedAt").exists())
                .andExpect(jsonPath("$.dateTime.own.timeZone").doesNotExist())
                .andExpect(jsonPath("$.dateTime.own.dateFormat").doesNotExist())
                .andExpect(jsonPath("$.dateTime.own.timeFormat").doesNotExist())
                .andExpect(jsonPath("$.dateTime.workspace.timeZone").value("Asia/Dubai"))
                .andExpect(jsonPath("$.dateTime.effective.dateFormat").value("DD_MMM_YYYY"))
                .andExpect(jsonPath("$.dateTime.effective.timeFormat").value("H24"));
    }

    @Test
    @DisplayName("a member follows the workspace's date and time until they choose their own, and can go back to it")
    void followsTheWorkspaceUntilChoosingTheirOwn() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Clock Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String member = login(sara);

        mvc.perform(putDateTime(admin, """
                        {"timeZone":"Europe/London","dateFormat":"MMM_D_YYYY","timeFormat":"H12"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime.timeZone").value("Europe/London"))
                .andExpect(jsonPath("$.dateTime.membersFollowing").value(2));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + member))
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.dateTime.effective.dateFormat").value("MMM_D_YYYY"))
                .andExpect(jsonPath("$.dateTime.effective.timeFormat").value("H12"));

        mvc.perform(patchProfile(member, """
                        {"fullName":"Sara Al-Mansour","title":null,"timezone":"Asia/Riyadh",
                         "dateFormat":"YYYY_MM_DD","timeFormat":null,"locale":"en"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.dateTime.own.timeZone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.dateTime.effective.dateFormat").value("YYYY_MM_DD"))
                .andExpect(jsonPath("$.dateTime.effective.timeFormat").value("H12"))
                .andExpect(jsonPath("$.dateTime.workspace.timeZone").value("Europe/London"));

        mvc.perform(patchProfile(member, """
                        {"fullName":"Sara Al-Mansour","title":null,"timezone":null,"locale":"en"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.dateTime.own.timeZone").doesNotExist())
                .andExpect(jsonPath("$.dateTime.effective.dateFormat").value("MMM_D_YYYY"));
    }

    @Test
    @DisplayName("the workspace's date and time are an admin's to set, from the fixed lists, audited once per change")
    void workspaceDateTimeIsAnAdminsAndAudited() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Admin Clock Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(putDateTime(login(sara), """
                        {"timeZone":"Europe/London"}"""))
                .andExpect(status().isForbidden());
        MvcResult refused = mvc.perform(putDateTime(admin, """
                        {"timeZone":"Asia/Tashkent"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timeZone").exists())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
        mvc.perform(putDateTime(admin, """
                        {"timeZone":"Asia/Riyadh","dateFormat":"DD_MM_YYYY","timeFormat":"BOGUS"}"""))
                .andExpect(status().isBadRequest());

        String chosen = """
                {"timeZone":"Asia/Riyadh","dateFormat":"DD_MM_YYYY","timeFormat":"H12"}""";
        mvc.perform(putDateTime(admin, chosen)).andExpect(status().isOk());
        mvc.perform(putDateTime(admin, chosen)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'WORKSPACE_UPDATED'
                 and metadata ->> 'section' = 'dateTime' and metadata ->> 'timeZoneFrom' = 'country'
                 and metadata ->> 'timeZoneTo' = 'Asia/Riyadh' and metadata ->> 'timeFormatTo' = 'H12'
                 and workspace_id = (select workspace_id from app_lm_workspace_member m join app_lm_user u
                                      on u.id = m.user_id where u.email = ?)""", Integer.class, alok))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime.timeZone").value("Asia/Riyadh"))
                .andExpect(jsonPath("$.dateTime.dateFormat").value("DD_MM_YYYY"))
                .andExpect(jsonPath("$.dateTime.suggestedTimeZone").value("Asia/Dubai"));

        mvc.perform(putDateTime(admin, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime.timeZone").doesNotExist())
                .andExpect(jsonPath("$.dateTime.dateFormat").doesNotExist());
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
    @DisplayName("a timezone off the fixed list — unknown, a legacy alias, or a region's zone not offered — is refused")
    void refusesUnknownTimezone() throws Exception {
        String token = adminOf("Alok Kumar", "Zone Firm");

        for (String refusedZone : List.of("Mars/Olympus", "EST", "Etc/GMT", "Asia/Tashkent")) {
            MvcResult refused = mvc.perform(patchProfile(token, """
                            {"fullName":"Alok Kumar","title":null,
                             "timezone":"%s","locale":"en"}""".formatted(refusedZone)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.timezone").exists())
                    .andReturn();
            assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    @DisplayName("a zone saved before the fixed list saves unchanged with the rest; only a new choice must be listed")
    void keepsALegacyZoneUntilItIsChanged() throws Exception {
        String token = adminOf("Alok Kumar", "Legacy Zone Firm");
        jdbc.update("update app_lm_user set timezone = 'US/Pacific' where email = ?", "alok@" + domain);

        mvc.perform(patchProfile(token, """
                        {"fullName":"Alok B Kumar","title":null,"timezone":"US/Pacific","locale":"en"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alok B Kumar"))
                .andExpect(jsonPath("$.timezone").value("US/Pacific"));

        mvc.perform(patchProfile(token, """
                        {"fullName":"Alok B Kumar","title":null,"timezone":"US/Eastern","locale":"en"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.timezone").exists());
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
                         "timezone":"UTC","locale":"fr"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alok Kumar"))
                .andExpect(jsonPath("$.timezone").value("Asia/Dubai"));
    }

    private MockHttpServletRequestBuilder putDateTime(String token, String body) {
        return put("/api/v1/workspace/date-time")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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

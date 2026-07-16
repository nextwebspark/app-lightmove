package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Settings → General: reading, renaming, defaults, and the typed-confirmation soft delete. */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class WorkspaceSettingsIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("any member reads the workspace detail, with a live member count")
    void memberReadsDetail() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Detail Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Detail Firm"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.emailDomain").value(domain));
    }

    @Test
    @DisplayName("renaming re-derives the logo mark and never touches the slug")
    void adminUpdatesSettings() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Old Name Firm");
        String admin = login(alok);

        mvc.perform(patch("/api/v1/workspace")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Zeta Advisory","defaultRegion":"MENA","defaultCurrency":"AED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zeta Advisory"))
                .andExpect(jsonPath("$.logoMark").value("Z"))
                .andExpect(jsonPath("$.defaultRegion").value("MENA"))
                .andExpect(jsonPath("$.defaultCurrency").value("AED"))
                .andExpect(jsonPath("$.slug", org.hamcrest.Matchers.startsWith("old-name-firm")));
    }

    @Test
    @DisplayName("a member may read the settings but not change them")
    void nonAdminCannotUpdate() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Locked Firm");
        inviteAndAccept(login(alok), "Sara Al-Mansour", sara, "MEMBER");

        mvc.perform(patch("/api/v1/workspace")
                        .header("Authorization", "Bearer " + login(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sara's Now"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deletion demands the workspace name typed back exactly")
    void deleteRequiresTypedName() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Careful Firm");
        String admin = login(alok);

        MvcResult wrong = mvc.perform(delete("/api/v1/workspace")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmName":"Wrong Firm"}"""))
                .andReturn();
        assertThat(wrong.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(wrong)).isEqualTo("WORKSPACE_NAME_MISMATCH");

        // Still alive.
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleting frees the members, kills pending invitations, and hides the workspace from signup")
    void deleteReleasesEverything() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Doomed Firm");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        // An invitation still outstanding when the workspace dies.
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"omar@%s","role":"MEMBER"}]
                                """.formatted(domain)))
                .andExpect(status().isOk());
        String omarLink = email.latestTokenFor("omar@" + domain);

        mvc.perform(delete("/api/v1/workspace")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmName":"doomed firm"}"""))
                .andExpect(status().isNoContent());

        // The admin has no workspace any more.
        String alokAgain = login(alok);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + alokAgain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace").doesNotExist());

        // The outstanding invitation died with the workspace.
        String omar = verifiedUser("Omar Khalil", "omar@" + domain);
        MvcResult refused = mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + omar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(omarLink)))
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("INVITATION_INVALID");

        // And both freed members can start again.
        createWorkspace(login(sara), "Sara Rises");
        createWorkspace(login(alok), "Alok Rises");
    }
}

package app.lightmove.api.core.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.email.model.EmailMessage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every template, built.
 *
 * <p>The point of building all of them is the sentences: a paragraph whose placeholders and phrases
 * disagree throws where it is formatted, which is inside the send — and a send sits in the middle of
 * signing up, inviting a colleague and seating a mandate. A template that cannot render must fail
 * here rather than there.
 */
class EmailTemplatesTest {

    private static final String BASE_URL = "https://beta.uncava.com";
    private static final String LINK = BASE_URL + "/auth/verify?token=abc123";
    private static final String PROJECT_LINK = BASE_URL + "/projects/8f2c";

    private final EmailTemplates templates = new EmailTemplates(new LightMoveProperties(
            null, null, new WebSettings(BASE_URL, List.of(), "/auth/callback", 0),
            null, null, null, null, null, null, null, null, null, null, null, null));

    @Test
    @DisplayName("every one renders, subject and both bodies, under one brand")
    void allRender() {
        assertThat(everyEmail()).hasSize(10).allSatisfy(message -> {
            assertThat(message.subject()).isNotBlank();
            assertThat(message.textBody()).isNotBlank();
            assertThat(message.htmlBody())
                    .contains(BASE_URL + "/brand/uncava-mark-email-v1.png")
                    .contains(">UNCAVA</span>");
        });
    }

    @Test
    @DisplayName("a staff member seated on a mandate is told which one, by whom, and as what")
    void addedToProjectNamesTheMandate() {
        EmailMessage message = templates.buildAddedToProjectEmail("nadia@x.ae", "Nadia Rahman",
                "Alok Sharma", "Group CFO", "Meridian Energy", "RESEARCHER", PROJECT_LINK);

        assertThat(message.subject()).isEqualTo("You were added to the Group CFO search on Uncava");
        assertThat(message.textBody())
                .contains("Alok Sharma added you to the Group CFO search for Meridian Energy "
                        + "as a researcher.")
                .contains(PROJECT_LINK);
        assertThat(message.htmlBody()).contains("<strong>Group CFO</strong>");
    }

    @Test
    @DisplayName("a role change says so rather than repeating the welcome")
    void roleChangeReadsAsAChange() {
        EmailMessage message = templates.buildProjectRoleChangedEmail("nadia@x.ae", "Nadia Rahman",
                "Alok Sharma", "Group CFO", "Meridian Energy", "LEAD", PROJECT_LINK);

        assertThat(message.subject()).isEqualTo("Your role on the Group CFO search changed");
        assertThat(message.textBody()).contains("You are now a lead on it.");
    }

    private List<EmailMessage> everyEmail() {
        return List.of(
                templates.buildVerificationEmail("n@x.ae", "Nadia Rahman", LINK),
                templates.buildPasswordResetEmail("n@x.ae", "Nadia Rahman", LINK),
                templates.buildAccountLockedEmail("n@x.ae", "Nadia Rahman", "14:30 today"),
                templates.buildPasswordChangedEmail("n@x.ae", "Nadia Rahman", LINK),
                templates.buildInvitationEmail("n@x.ae", "Alok Sharma", "Alac Partners", "MEMBER", LINK),
                templates.buildClientInvitationEmail("n@x.ae", "Alok Sharma", "Alac Partners",
                        "Meridian Energy", LINK),
                templates.buildRepresentativeAddedEmail("n@x.ae", "Nadia Rahman", "Alok Sharma",
                        "Alac Partners", "Meridian Energy"),
                templates.buildAttachedToMandateEmail("n@x.ae", "Nadia Rahman", "Alok Sharma",
                        "Meridian Energy", "Group CFO"),
                templates.buildAddedToProjectEmail("n@x.ae", "Nadia Rahman", "Alok Sharma", "Group CFO",
                        "Meridian Energy", "RESEARCHER", PROJECT_LINK),
                templates.buildProjectRoleChangedEmail("n@x.ae", "Nadia Rahman", "Alok Sharma",
                        "Group CFO", "Meridian Energy", "LEAD", PROJECT_LINK));
    }
}

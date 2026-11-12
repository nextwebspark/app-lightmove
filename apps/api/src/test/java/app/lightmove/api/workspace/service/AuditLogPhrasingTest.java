package app.lightmove.api.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.AuditLine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogPhrasingTest {

    private static final String SECRET = "secret-value@leak.example";
    private static final List<String> NEVER_ON_SCREEN = List.of("triage", "seat", "grant", "enrich", "nylas",
            "contactout", "bright data", "apollo");

    private final AuditLogPhrasing phrasing = new AuditLogPhrasing();

    @Test
    @DisplayName("every known type phrases from allowlisted keys alone, and no line carries an internal word")
    void everyTypePhrasesSafely() {
        Map<String, Object> metadata = new HashMap<>();
        for (String key : List.of("email", "name", "fullName", "companyName", "domain", "slug", "endpoint",
                "adminConsentTenant", "removedUserId", "ownerUserId", "keyId", "credentialId", "clientId")) {
            metadata.put(key, SECRET);
        }
        allCodes().forEach(code -> {
            for (AuditOutcome outcome : AuditOutcome.values()) {
                for (WorkspaceMode mode : WorkspaceMode.values()) {
                    String line = phrasing.phrase(new AuditLine(code, outcome, metadata, null, mode));
                    assertThat(line).as(code).isNotBlank().doesNotContain(SECRET);
                    assertThat(NEVER_ON_SCREEN).as(code + ": " + line)
                            .noneMatch(word -> line.toLowerCase().contains(word));
                }
            }
        });
    }

    @Test
    @DisplayName("a key off the allowlist cannot be read, so no phrase can quote an email or a name")
    void forbiddenKeysAreUnreadable() {
        AuditLine line = new AuditLine("MEMBER_INVITED", AuditOutcome.SUCCESS, Map.of("email", SECRET), null,
                WorkspaceMode.COMPANY);
        assertThat(AuditLine.READABLE_KEYS).doesNotContain("email", "name", "fullName", "companyName");
        assertThatThrownBy(() -> line.text("email")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lines name the resolved target and speak the workspace's own words")
    void phrasesNameTheirTarget() {
        assertThat(phrase("MEMBER_ROLE_CHANGED", Map.of("from", "MEMBER", "to", "ADMIN,MEMBER"), "Mira Saleh",
                WorkspaceMode.COMPANY)).isEqualTo("Changed Mira Saleh's roles from Member to Admin and Member");
        assertThat(phrase("MEMBER_INVITED", Map.of("type", "client"), "omar@client.example", WorkspaceMode.AGENCY))
                .isEqualTo("Invited omar@client.example as a client contact");
        assertThat(phrase("MEMBER_INVITED", Map.of("type", "client"), null, WorkspaceMode.COMPANY))
                .isEqualTo("Invited someone as a hiring manager");
        assertThat(phrase("COMPANIES_EXPORTED", Map.of("rows", "12", "stage", "shortlisted"), null,
                WorkspaceMode.COMPANY)).isEqualTo("Exported 12 companies from Shortlisted");
        assertThat(phrase("LOGIN_SUCCEEDED", Map.of("secondFactor", "TOTP"), null, WorkspaceMode.COMPANY))
                .isEqualTo("Signed in with two-factor authentication");
        assertThat(phrase("OAUTH_LOGIN_SUCCEEDED", Map.of("provider", "linkedin"), null, WorkspaceMode.COMPANY))
                .isEqualTo("Signed in with LinkedIn");
    }

    @Test
    @DisplayName("an unphrased type reads as its own name in the screens' words; an unknown one as Other activity")
    void unphrasedTypes() {
        assertThat(AuditLogPhrasing.humanised("TRIAGE_COMPANY_MOVED")).isEqualTo("Company moved");
        assertThat(AuditLogPhrasing.humanised("PERSON_NOTE_ADDED")).isEqualTo("Candidate note added");
        assertThat(phrase("NOT_A_REAL_TYPE", Map.of(), null, WorkspaceMode.COMPANY)).isEqualTo("Other activity");
    }

    private String phrase(String code, Map<String, Object> metadata, String targetName, WorkspaceMode mode) {
        return phrasing.phrase(new AuditLine(code, AuditOutcome.SUCCESS, metadata, targetName, mode));
    }

    private static Stream<String> allCodes() {
        return Stream.of(AuthEventType.values(), WorkspaceEventType.values(), ProjectEventType.values(),
                        SecurityEventType.values(), PlatformEventType.values())
                .flatMap(Arrays::stream)
                .map(AuditEventType::code);
    }
}

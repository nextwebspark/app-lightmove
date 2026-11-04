package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.outreach.model.EmailAttachment;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.service.OutreachDispatcher;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Phase 5 of docs/outreach-conversations.md: a consultant's own email carries files — one uploaded in the composer,
 * or one of the person's documents, copied when the email is written — checked by name and bytes and held to the
 * limits before anything is stored, sent once, recorded as a disclosure, and deleted with a cancelled email.
 */
@IntegrationTest
class OutreachAttachmentIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "attaching@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private OutreachDispatcher dispatcher;
    @Autowired private DocumentStore store;
    @Autowired @FixtureJdbc private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String priya;
    private String priyaAddress;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        jdbc.update("update app_lm_outreach_enrollment set status = 'STOPPED', stop_reason = 'MANUAL', "
                + "next_send_at = null, sending_since = null where status in ('SCHEDULED', 'ACTIVE')");
        jdbc.update("update app_lm_outreach_message set status = 'CANCELLED', sending_since = null "
                + "where status = 'SCHEDULED'");
        String consultantEmail = "lina@" + domain;
        // Its own name: the suite shares one database, and a name runs out of free slugs after 99 workspaces.
        createWorkspace(verifiedUser("Lina Saad", consultantEmail), "Attachment Partners");
        consultant = login(consultantEmail);
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}")).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        projectId = body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        gateway.grant(new GrantedMailbox("grant-" + domain, MAILBOX, "google"));
        connectMailbox();
        priyaAddress = "priya@" + domain;
        priya = executive("Priya Raman", priyaAddress);
    }

    @Test
    @DisplayName("an upload and a person's document go with the email, in order, and the document's leaving is recorded")
    void filesGoWithTheEmail() throws Exception {
        String versionId = uploadDocument(priya, pdf("file", "Priya CV.pdf", "her CV"));
        JsonNode scheduled = sendWithFiles(status().isCreated(), message(List.of(versionId)),
                pdf("files", "Brief.pdf", "the brief"));

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        OutgoingEmail email = gateway.sent().getLast().email();
        assertThat(email.attachments()).extracting(EmailAttachment::fileName).containsExactly("Brief.pdf", "Priya CV.pdf");
        assertThat(email.attachments()).extracting(EmailAttachment::contentType)
                .containsOnly("application/pdf");
        assertThat(new String(email.attachments().get(1).content(), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-1.7\nher CV");
        assertThat(email.toString()).doesNotContain("her CV");
        assertThat(auditCount("PERSON_DOCUMENT_SENT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select metadata::text from app_lm_audit_event where event_type = "
                + "'PERSON_DOCUMENT_SENT' and metadata->>'messageId' = ?", String.class, scheduled.get("id").asText()))
                .as("a CV's name is usually its person's: the trail keeps ids").contains(versionId)
                .doesNotContain("Priya CV");

        JsonNode line = conversationLine(scheduled.get("id").asText());
        assertThat(line.at("/attachments/0/fileName").asText()).isEqualTo("Brief.pdf");
        assertThat(line.at("/attachments/1/sizeBytes").asLong()).isEqualTo("%PDF-1.7\nher CV".length());
        String attachmentId = line.at("/attachments/0/id").asText();
        MvcResult download = as(consultant, get(outreach("/messages/" + scheduled.get("id").asText()
                + "/attachments/" + attachmentId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(download.getResponse().getContentAsString()).isEqualTo("%PDF-1.7\nthe brief");
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("attachment");
        assertThat(auditCount("OUTREACH_ATTACHMENT_DOWNLOADED")).isEqualTo(1);
    }

    @Test
    @DisplayName("a document removed after the email was written still goes as it was attached")
    void aCopyOutlivesItsDocument() throws Exception {
        String versionId = uploadDocument(priya, pdf("file", "Priya CV.pdf", "as attached"));
        String documentId = jdbc.queryForObject(
                "select document_id::text from app_lm_person_document_version where id = ?::uuid", String.class,
                versionId);
        sendWithFiles(status().isCreated(), message(List.of(versionId)));
        as(consultant, delete("/api/v1/projects/" + projectId + "/candidates/" + priya + "/documents/" + documentId))
                .andExpect(status().is2xxSuccessful());

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        OutgoingEmail email = gateway.sent().getLast().email();
        assertThat(email.attachments()).hasSize(1);
        assertThat(new String(email.attachments().getFirst().content(), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-1.7\nas attached");
        assertThat(auditCount("PERSON_DOCUMENT_SENT")).as("still a document leaving").isEqualTo(1);
    }

    @Test
    @DisplayName("a file whose bytes are not what its name says, or too large, or one too many, is refused unwritten")
    void filesAreCheckedFirst() throws Exception {
        MockMultipartFile disguised = new MockMultipartFile("files", "Brief.pdf", "application/pdf",
                "MZ\u0090 not a pdf at all".getBytes(StandardCharsets.ISO_8859_1));
        sendExpecting(status().isBadRequest(), message(List.of()), disguised)
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        sendExpecting(status().isPayloadTooLarge(), message(List.of()), pdfOfSize("Big.pdf", 3_000_001))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        MockMultipartFile[] six = new MockMultipartFile[6];
        Arrays.setAll(six, index -> pdf("files", "Part " + index + ".pdf", "part " + index));
        sendExpecting(status().isBadRequest(), message(List.of()), six)
                .andExpect(jsonPath("$.code").value("OUTREACH_TOO_MANY_ATTACHMENTS"));

        MockMultipartFile[] heavy = new MockMultipartFile[4];
        Arrays.setAll(heavy, index -> pdfOfSize("Heavy " + index + ".pdf", 2_900_000));
        sendExpecting(status().isPayloadTooLarge(), message(List.of()), heavy)
                .andExpect(jsonPath("$.code").value("OUTREACH_ATTACHMENTS_TOO_LARGE"));

        assertThat(messagesToPriya()).isZero();
        assertThat(attachmentRows()).isZero();
    }

    @Test
    @DisplayName("another person's document is not found, and nothing is written")
    void onlyTheirOwnDocuments() throws Exception {
        String omar = executive("Omar Khalil", "omar@" + domain);
        String hisVersion = uploadDocument(omar, pdf("file", "Omar CV.pdf", "his CV"));

        sendExpecting(status().isNotFound(), message(List.of(hisVersion)));

        assertThat(messagesToPriya()).isZero();
    }

    @Test
    @DisplayName("a refused email takes its stored files with it, and a cancelled one deletes them")
    void storedFilesGoWithTheEmail() throws Exception {
        markDoNotContact();
        sendExpecting(status().isConflict(), message(List.of()), pdf("files", "Brief.pdf", "refused"));
        assertThat(attachmentRows()).isZero();
        jdbc.update("update app_lm_person set do_not_contact = false, do_not_contact_reason = null, "
                + "do_not_contact_set_by = null, do_not_contact_set_at = null where id = "
                + "(select person_id from app_lm_project_candidate where id = ?::uuid)", priya);

        String later = Instant.now().plus(Duration.ofHours(2)).toString();
        JsonNode scheduled = sendWithFiles(status().isCreated(), """
                {"toAddress":"%s","subject":"Later","body":"Hello","replyInThread":false,"sendAt":"%s"}"""
                .formatted(priyaAddress, later), pdf("files", "Brief.pdf", "to cancel"));
        String key = jdbc.queryForObject("select storage_key from app_lm_outreach_message_attachment "
                + "where message_id = ?::uuid", String.class, scheduled.get("id").asText());
        try (InputStream stored = store.open(key)) {
            assertThat(stored.readAllBytes()).isNotEmpty();
        }

        as(consultant, delete(outreach("/messages/" + scheduled.get("id").asText())))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> store.open(key).close()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("a file that cannot be read at send fails the email unsent, and never goes without it")
    void anUnreadableFileFailsTheEmail() throws Exception {
        JsonNode scheduled = sendWithFiles(status().isCreated(), message(List.of()), pdf("files", "Brief.pdf", "gone"));
        store.delete(storageKeyOf(scheduled.get("id").asText()));

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        assertThat(gateway.sent()).isEmpty();
        assertThat(jdbc.queryForMap("select status, fail_reason from app_lm_outreach_message where id = ?::uuid",
                scheduled.get("id").asText())).containsEntry("status", "FAILED").containsEntry("fail_reason", "SEND_FAILED");
    }

    @Test
    @DisplayName("an email that definitely never went keeps no copy: its files are deleted and no longer offered")
    void aFailedEmailKeepsNoCopy() throws Exception {
        JsonNode scheduled = sendWithFiles(status().isCreated(), message(List.of()), pdf("files", "Brief.pdf", "unsent"));
        String id = scheduled.get("id").asText();
        String key = storageKeyOf(id);
        String attachmentId = attachmentIdOf(id);
        markDoNotContact();

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        assertThat(gateway.sent()).isEmpty();
        assertThatThrownBy(() -> store.open(key).close()).isInstanceOf(IOException.class);
        assertThat(conversationLine(id).get("attachments")).isEmpty();
        as(consultant, get(outreach("/messages/" + id + "/attachments/" + attachmentId))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a file downloads only from its own position and email, never a cancelled one's, and never for a client seat")
    void downloadsAreHeldToTheirEmail() throws Exception {
        String later = Instant.now().plus(Duration.ofHours(2)).toString();
        String id = sendWithFiles(status().isCreated(), """
                {"toAddress":"%s","subject":"Later","body":"Hello","replyInThread":false,"sendAt":"%s"}"""
                .formatted(priyaAddress, later), pdf("files", "Brief.pdf", "held")).get("id").asText();
        String attachmentId = attachmentIdOf(id);
        as(consultant, get(outreach("/messages/" + id + "/attachments/" + attachmentId))).andExpect(status().isOk());

        String otherProject = body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group COO\"}".formatted(clientIdOf())))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        as(consultant, get("/api/v1/projects/" + otherProject + "/outreach/messages/" + id + "/attachments/"
                + attachmentId)).andExpect(status().isNotFound());
        as(clientSeat(), get(outreach("/messages/" + id + "/attachments/" + attachmentId)))
                .andExpect(status().isForbidden());

        as(consultant, delete(outreach("/messages/" + id))).andExpect(status().isNoContent());
        as(consultant, get(outreach("/messages/" + id + "/attachments/" + attachmentId))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("archiving the position deletes a waiting email's files with it")
    void archivingDeletesWaitingFiles() throws Exception {
        String later = Instant.now().plus(Duration.ofDays(1)).toString();
        String id = sendWithFiles(status().isCreated(), """
                {"toAddress":"%s","subject":"Later","body":"Hello","replyInThread":false,"sendAt":"%s"}"""
                .formatted(priyaAddress, later), pdf("files", "Brief.pdf", "archived")).get("id").asText();
        String key = storageKeyOf(id);

        as(consultant, post("/api/v1/projects/" + projectId + "/archive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmName\":\"Group CFO\"}")).andExpect(status().is2xxSuccessful());

        assertThatThrownBy(() -> store.open(key).close()).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("the JSON request carries a person's document without any upload")
    void theJsonRequestCarriesDocuments() throws Exception {
        String versionId = uploadDocument(priya, pdf("file", "Priya CV.pdf", "by json"));
        as(consultant, post(outreach("/conversations/" + priya + "/messages")).contentType(MediaType.APPLICATION_JSON)
                .content(message(List.of(versionId)))).andExpect(status().isCreated());

        dispatcher.dispatchAt(Instant.now().plusSeconds(5));

        assertThat(gateway.sent().getLast().email().attachments()).extracting(EmailAttachment::fileName)
                .containsExactly("Priya CV.pdf");
    }

    @Test
    @DisplayName("a refused email leaves no stored file behind")
    void aRefusedEmailStoresNothing() throws Exception {
        markDoNotContact();
        sendExpecting(status().isConflict(), message(List.of()), pdf("files", "Brief.pdf", "refused"),
                pdf("files", "Brief 2.pdf", "refused too"));

        Path stored = Path.of("target/test-documents", "ws", workspaceIdOf(), "outreach", "attachments");
        if (Files.exists(stored)) {
            try (Stream<Path> files = Files.list(stored)) {
                assertThat(files).isEmpty();
            }
        }
    }

    private JsonNode sendWithFiles(ResultMatcher expected, String json, MockMultipartFile... files) throws Exception {
        return body(sendExpecting(expected, json, files).andReturn());
    }

    private ResultActions sendExpecting(ResultMatcher expected, String json, MockMultipartFile... files)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(outreach("/conversations/" + priya + "/messages"))
                .file(new MockMultipartFile("message", "message.json", MediaType.APPLICATION_JSON_VALUE,
                        json.getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return mvc.perform(request.header("Authorization", "Bearer " + consultant)).andExpect(expected);
    }

    private String storageKeyOf(String messageId) {
        return jdbc.queryForObject("select storage_key from app_lm_outreach_message_attachment where message_id = ?::uuid",
                String.class, messageId);
    }

    private String attachmentIdOf(String messageId) {
        return jdbc.queryForObject("select id::text from app_lm_outreach_message_attachment where message_id = ?::uuid",
                String.class, messageId);
    }

    private String workspaceIdOf() {
        return jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid", String.class,
                projectId);
    }

    private String clientIdOf() {
        return jdbc.queryForObject("select client_id::text from app_lm_project where id = ?::uuid", String.class,
                projectId);
    }

    private String clientSeat() throws Exception {
        String clientEmail = "client@attachment-client-" + domain;
        as(consultant, post("/api/v1/projects/" + projectId + "/representatives/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"A Client","position":"Chair","email":"%s"}
                        """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private String message(List<String> documentVersionIds) {
        String ids = String.join(",", documentVersionIds.stream().map(id -> "\"" + id + "\"").toList());
        return """
                {"toAddress":"%s","subject":"The brief","body":"Attached.","replyInThread":false,
                 "documentVersionIds":[%s]}""".formatted(priyaAddress, ids);
    }

    private String uploadDocument(String candidateId, MockMultipartFile file) throws Exception {
        JsonNode uploaded = body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/candidates/"
                        + candidateId + "/documents").file(file).header("Authorization", "Bearer " + consultant))
                .andExpect(status().isCreated()).andReturn());
        return uploaded.at("/document/versions/0/id").asText();
    }

    private JsonNode conversationLine(String messageId) throws Exception {
        JsonNode conversation = body(as(consultant, get(outreach("/conversations/" + priya)))
                .andExpect(status().isOk()).andReturn());
        for (JsonNode event : conversation.get("events")) {
            if ("MESSAGE".equals(event.get("type").asText())
                    && messageId.equals(event.at("/message/id").asText())) {
                return event.get("message");
            }
        }
        throw new AssertionError("no line " + messageId + " in the conversation");
    }

    private void markDoNotContact() throws Exception {
        String personId = jdbc.queryForObject("select person_id::text from app_lm_project_candidate where id = ?::uuid",
                String.class, priya);
        as(consultant, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private long auditCount(String eventType) {
        return jdbc.queryForObject("select count(*) from app_lm_audit_event e where e.event_type = ? "
                + "and e.workspace_id = (select workspace_id from app_lm_project where id = ?::uuid)", Long.class,
                eventType, projectId);
    }

    private long messagesToPriya() {
        return jdbc.queryForObject("select count(*) from app_lm_outreach_message where candidate_id = ?::uuid",
                Long.class, priya);
    }

    private long attachmentRows() {
        return jdbc.queryForObject("select count(*) from app_lm_outreach_message_attachment a "
                + "join app_lm_outreach_message m on m.id = a.message_id where m.candidate_id = ?::uuid", Long.class,
                priya);
    }

    private static MockMultipartFile pdf(String part, String fileName, String text) {
        return new MockMultipartFile(part, fileName, "application/pdf",
                ("%PDF-1.7\n" + text).getBytes(StandardCharsets.US_ASCII));
    }

    private static MockMultipartFile pdfOfSize(String fileName, int sizeBytes) {
        byte[] content = new byte[sizeBytes];
        Arrays.fill(content, (byte) 'x');
        System.arraycopy("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 9);
        return new MockMultipartFile("files", fileName, "application/pdf", content);
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + emailAddress.hashCode();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", state).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }
}

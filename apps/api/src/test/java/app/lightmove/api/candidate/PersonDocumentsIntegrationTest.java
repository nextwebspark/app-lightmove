package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * A person's documents (V101): one library whichever mandate they are read from, a file sent again
 * under its name stacking as a version, the same bytes refused, a file judged by its bytes rather than
 * its name, every change a timeline line that names the document only while it exists, and no client
 * seat reaching any of it.
 */
@IntegrationTest
class PersonDocumentsIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("a CV uploaded on one position is read from every other, filed as the person's CV")
    void aDocumentIsSharedAcrossPositions() throws Exception {
        firm("Shared Documents Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        JsonNode onCfo = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-docs"}""");
        JsonNode onCredit = add(credit, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-docs"}""");

        JsonNode uploaded = upload(admin, positionUrl(cfo, onCfo), pdf("Fatima_CV.pdf", "first"), null);
        assertThat(uploaded.get("outcome").asText()).isEqualTo("created");
        JsonNode card = uploaded.get("document");
        assertThat(card.get("category").asText()).isEqualTo("cv");
        assertThat(card.get("title").asText()).isEqualTo("Fatima_CV");
        assertThat(card.get("primaryCv").asBoolean()).isTrue();
        assertThat(card.get("projectTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(card.get("versions").get(0).get("versionNo").asInt()).isEqualTo(1);
        assertThat(card.get("versions").get(0).get("contentType").asText()).isEqualTo("application/pdf");
        assertThat(card.get("versions").get(0).get("previewable").asBoolean()).isTrue();

        JsonNode fromCredit = list(admin, positionUrl(credit, onCredit));
        assertThat(fromCredit).hasSize(1);
        assertThat(fromCredit.get(0).get("id").asText()).isEqualTo(card.get("id").asText());
        JsonNode fromPool = list(admin, personUrl(onCfo.get("personId").asText()));
        assertThat(fromPool.get(0).get("id").asText()).isEqualTo(card.get("id").asText());
    }

    @Test
    @DisplayName("the same name is the next version, unless asked to stand alone; the same bytes are refused")
    void aSameNameIsAVersionAndTheSameBytesARefusal() throws Exception {
        firm("Versions Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Khalid Al Harbi"}""");
        String url = positionUrl(cfo, filed);

        String documentId = upload(admin, url, pdf("Khalid CV.pdf", "2025 edition"), null)
                .get("document").get("id").asText();
        JsonNode second = upload(admin, url, pdf("khalid cv.pdf", "2026 edition"), null);
        assertThat(second.get("outcome").asText()).isEqualTo("new_version");
        assertThat(second.get("document").get("id").asText()).isEqualTo(documentId);
        assertThat(versionNumbersOf(second.get("document"))).containsExactly(2, 1);

        JsonNode apart = upload(admin, url, pdf("Khalid CV.pdf", "a tailored copy"), "asNewDocument");
        assertThat(apart.get("outcome").asText()).isEqualTo("created");
        assertThat(apart.get("document").get("id").asText()).isNotEqualTo(documentId);
        assertThat(apart.get("document").get("primaryCv").asBoolean()).isFalse();

        JsonNode explicit = body(mvc.perform(multipart(url + "/" + documentId + "/versions")
                        .file(pdf("CV_Khalid_final.pdf", "renamed by the candidate"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(versionNumbersOf(explicit.get("document"))).containsExactly(3, 2, 1);

        MvcResult refused = mvc.perform(multipart(url).file(pdf("Copy of Khalid CV.pdf", "2026 edition"))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("PERSON_DOCUMENT_DUPLICATE");
        assertThat(body(refused).get("duplicateOf").get("documentId").asText()).isEqualTo(documentId);
        assertThat(body(refused).get("duplicateOf").get("versionNo").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("a file is judged by its bytes: a renamed executable and an oversized file are refused")
    void aFileIsJudgedByItsBytes() throws Exception {
        firm("File Checks Firm");
        String cfo = mandate("Chief Financial Officer");
        String url = positionUrl(cfo, add(cfo, """
                {"fullName":"Hamad Al Suwaidi"}"""));

        MvcResult disguised = mvc.perform(multipart(url)
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf",
                                new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03}))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(codeOf(disguised)).isEqualTo("UNSUPPORTED_FILE_TYPE");

        MvcResult oversized = mvc.perform(multipart(url).file(pdf("cv.pdf", "x".repeat(5000)))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isPayloadTooLarge())
                .andReturn();
        assertThat(codeOf(oversized)).isEqualTo("FILE_TOO_LARGE");

        mvc.perform(multipart(url).file(pdf("cv.pdf", "fine")).param("category", "memo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        assertThat(list(admin, url)).isEmpty();
    }

    @Test
    @DisplayName("a preview of a PDF is inline as a PDF; a Word file always downloads, and both are audited")
    void downloadsAreTypedAndAudited() throws Exception {
        firm("Downloads Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Mariam Al Kaabi"}""");
        String url = positionUrl(cfo, filed);
        JsonNode pdfCard = upload(admin, url, pdf("Mariam CV.pdf", "body"), null).get("document");
        byte[] docx = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00};
        JsonNode docxCard = upload(admin, url, new MockMultipartFile("file", "Reference letter.docx",
                "application/octet-stream", docx), null).get("document");
        assertThat(docxCard.get("category").asText()).isEqualTo("reference");

        MvcResult preview = mvc.perform(get(contentUrl(url, pdfCard)).param("preview", "true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(preview.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(preview.getResponse().getContentAsString()).startsWith("%PDF-");

        MvcResult word = mvc.perform(get(contentUrl(url, docxCard)).param("preview", "true")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andReturn();
        assertThat(word.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(word.getResponse().getContentAsByteArray()).isEqualTo(docx);

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'PERSON_DOCUMENT_DOWNLOADED' AND metadata ->> 'documentId' IN (?, ?)""",
                Integer.class, pdfCard.get("id").asText(), docxCard.get("id").asText())).isEqualTo(2);
    }

    @Test
    @DisplayName("a document is removed by whoever filed it or an admin; the CV mark passes to the next CV")
    void onlyItsUploaderOrAnAdminRemovesADocument() throws Exception {
        firm("Removal Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Noura Al Thani"}""");
        String url = positionUrl(cfo, filed);
        String sara = researcherOn(cfo);

        String adminsCv = upload(admin, url, pdf("Noura CV.pdf", "admin's copy"), null)
                .get("document").get("id").asText();
        JsonNode sarasCv = upload(sara, url, pdf("Noura CV long.pdf", "sara's copy"), null).get("document");
        assertThat(sarasCv.get("primaryCv").asBoolean()).isFalse();

        MvcResult refused = mvc.perform(delete(url + "/" + adminsCv).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("PERSON_DOCUMENT_NOT_YOURS");
        assertThat(list(sara, url).get(0).get("removable").asBoolean()).isFalse();

        mvc.perform(delete(url + "/" + adminsCv).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        JsonNode left = list(admin, url);
        assertThat(left).hasSize(1);
        assertThat(left.get(0).get("id").asText()).isEqualTo(sarasCv.get("id").asText());
        assertThat(left.get(0).get("primaryCv").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("the CV mark moves on request, and only a CV can hold it")
    void theCvMarkMoves() throws Exception {
        firm("Primary CV Firm");
        String cfo = mandate("Chief Financial Officer");
        String url = positionUrl(cfo, add(cfo, """
                {"fullName":"Rajesh Menon"}"""));
        String first = upload(admin, url, pdf("Rajesh CV.pdf", "one"), null).get("document").get("id").asText();
        String second = upload(admin, url, pdf("Rajesh resume board.pdf", "two"), null)
                .get("document").get("id").asText();
        String letter = upload(admin, url, pdf("Rajesh cover letter.pdf", "three"), null)
                .get("document").get("id").asText();

        JsonNode moved = body(mvc.perform(patch(url + "/" + second)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primaryCv":true,"title":"Board CV"}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(moved.get("primaryCv").asBoolean()).isTrue();
        assertThat(moved.get("title").asText()).isEqualTo("Board CV");
        JsonNode cards = list(admin, url);
        assertThat(cards.get(0).get("id").asText()).isEqualTo(second);
        assertThat(cards).filteredOn(card -> card.get("id").asText().equals(first))
                .allSatisfy(card -> assertThat(card.get("primaryCv").asBoolean()).isFalse());

        mvc.perform(patch(url + "/" + letter)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primaryCv":true}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("every change is a timeline line naming the document while it exists, and never after")
    void theTimelineNamesALiveDocumentOnly() throws Exception {
        firm("Document Timeline Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Aisha Karim"}""");
        String url = positionUrl(cfo, filed);
        JsonNode card = upload(admin, url, pdf("Aisha Karim CV.pdf", "v1"), null).get("document");
        String documentId = card.get("id").asText();
        JsonNode second = upload(admin, url, pdf("Aisha Karim CV.pdf", "v2"), null).get("document");
        String olderVersion = second.get("versions").get(1).get("id").asText();

        JsonNode lines = timeline(cfo, filed, "documents").get("entries");
        assertThat(kindsOf(lines)).containsExactly("DOCUMENT_VERSION_ADDED", "DOCUMENT_ADDED");
        assertThat(lines.get(0).get("details").get("document").asText()).isEqualTo("Aisha Karim CV");
        assertThat(lines.get(0).get("details").get("version").asText()).isEqualTo("2");
        assertThat(lines.get(0).get("projectTitle").asText()).isEqualTo("Chief Financial Officer");

        mvc.perform(delete(url + "/" + documentId + "/versions/" + olderVersion)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(versionNumbersOf(list(admin, url).get(0))).containsExactly(2);

        mvc.perform(delete(url + "/" + documentId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        JsonNode after = timeline(cfo, filed, "documents").get("entries");
        assertThat(kindsOf(after)).containsExactly("DOCUMENT_REMOVED", "DOCUMENT_VERSION_REMOVED",
                "DOCUMENT_VERSION_ADDED", "DOCUMENT_ADDED");
        assertThat(after).allSatisfy(line -> assertThat(line.get("details").has("document")).isFalse());
        assertThat(db.queryForObject("""
                select count(*) from app_lm_person_activity
                where person_id = ?::uuid and details::text like '%Aisha Karim CV%'""", Integer.class,
                filed.get("personId").asText())).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_person_document_version v "
                + "join app_lm_person_document d on d.id = v.document_id where d.id = ?::uuid", Integer.class,
                documentId)).isZero();
    }

    @Test
    @DisplayName("a client seat reaches no document by any route, and another workspace's person is not found")
    void aClientSeatAndAnOutsiderReachNone() throws Exception {
        firm("Client Documents Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Lina Said"}""");
        String url = positionUrl(cfo, filed);
        JsonNode card = upload(admin, url, pdf("Lina CV.pdf", "confidential"), null).get("document");
        String client = clientOn(cfo);

        mvc.perform(get(url).header("Authorization", "Bearer " + client)).andExpect(status().isForbidden());
        mvc.perform(get(contentUrl(url, card)).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(url).file(pdf("Lina CV 2.pdf", "from a client"))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(get(personUrl(filed.get("personId").asText())).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());

        String outsiderEmail = "nadia@other-" + domain;
        createWorkspace(verifiedUser("Nadia Rahman", outsiderEmail), "Theirs Firm");
        String outsider = login(outsiderEmail);
        mvc.perform(get(personUrl(filed.get("personId").asText())).header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
        mvc.perform(get(personUrl(filed.get("personId").asText()) + "/" + card.get("id").asText() + "/versions/"
                        + card.get("versions").get(0).get("id").asText() + "/content")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
    }

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** Sara, a MEMBER, seated as RESEARCHER on {@code projectId}; returns her token. */
    private String researcherOn(String projectId) throws Exception {
        String saraEmail = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", saraEmail, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, saraEmail))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(saraEmail);
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@documents-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
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

    private JsonNode add(String projectId, String json) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn());
    }

    /** {@code flag} is {@code asNewDocument} to ask for a document of its own, or null. */
    private JsonNode upload(String token, String url, MockMultipartFile file, String flag) throws Exception {
        var request = multipart(url).file(file).header("Authorization", "Bearer " + token);
        if (flag != null) {
            request = request.param(flag, "true");
        }
        return body(mvc.perform(request).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode list(String token, String url) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode timeline(String projectId, JsonNode filed, String group) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/candidates/" + filed.get("id").asText()
                                + "/timeline").param("group", group)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** A PDF by its signature; {@code text} keeps each fixture's bytes, and so its hash, its own. */
    private static MockMultipartFile pdf(String fileName, String text) {
        return new MockMultipartFile("file", fileName, "application/pdf",
                ("%PDF-1.7\n" + text).getBytes(StandardCharsets.US_ASCII));
    }

    private static List<Integer> versionNumbersOf(JsonNode card) {
        List<Integer> numbers = new ArrayList<>();
        card.get("versions").forEach(version -> numbers.add(version.get("versionNo").asInt()));
        return numbers;
    }

    private static List<String> kindsOf(JsonNode entries) {
        List<String> kinds = new ArrayList<>();
        entries.forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }

    private static String positionUrl(String projectId, JsonNode filed) {
        return "/api/v1/projects/" + projectId + "/candidates/" + filed.get("id").asText() + "/documents";
    }

    private static String personUrl(String personId) {
        return "/api/v1/candidates/" + personId + "/documents";
    }

    private static String contentUrl(String url, JsonNode card) {
        return url + "/" + card.get("id").asText() + "/versions/" + card.get("versions").get(0).get("id").asText()
                + "/content";
    }
}

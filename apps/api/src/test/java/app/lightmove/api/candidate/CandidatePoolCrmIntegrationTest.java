package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The workspace's Candidates page (Phase 4, V98): the pool read with its search, quick views and
 * filters, and the team's own facts about a person — owner, tags, do not contact — each a timeline line
 * and none of it reachable by a client seat.
 */
@IntegrationTest
class CandidatePoolCrmIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private String admin;
    private String adminId;

    @Test
    @DisplayName("the pool is searched by name, title, employer or email, and each quick view is counted")
    void thePoolIsSearchedAndCounted() throws Exception {
        firm("Pool Search Firm");
        String cfo = mandate("Chief Financial Officer");
        add(cfo, """
                {"fullName":"Fatima Al Mazrouei","title":"Group CFO","employerName":"Aldar Properties",
                 "emails":[{"value":"fatima@aldar.example"}]}""");
        add(cfo, """
                {"fullName":"Rajesh Menon","title":"Finance Director","employerName":"Emaar"}""");

        assertThat(namesOf(pool(Map.of("q", "aldar")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("q", "finance director")))).containsExactly("Rajesh Menon");
        assertThat(namesOf(pool(Map.of("q", "FATIMA@ALDAR")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("q", "50%")))).isEmpty();

        JsonNode everyone = pool(Map.of());
        assertThat(body(mvc.perform(get("/api/v1/candidates/count").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("count").asLong()).isEqualTo(2);
        assertThat(everyone.get("totalCount").asLong()).isEqualTo(2);
        assertThat(everyone.get("poolSize").asLong()).isEqualTo(2);
        assertThat(everyone.get("viewCounts").get("active").asLong()).isEqualTo(2);
        assertThat(everyone.get("viewCounts").get("unplaced").asLong()).isZero();
        assertThat(everyone.get("viewCounts").get("mine").asLong()).isZero();
        JsonNode fatima = rowNamed(everyone, "Fatima Al Mazrouei");
        assertThat(fatima.get("companyName").asText()).isEqualTo("Aldar Properties");
        assertThat(fatima.get("positions").get(0).get("positionTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(fatima.get("lastActivity").get("kind").asText()).isEqualTo("ADDED_TO_POOL");
    }

    @Test
    @DisplayName("tags are seeded, created by anyone, matched any/all/none, and a retired one is never put on anyone")
    void tagsFilterThePool() throws Exception {
        firm("Pool Tags Firm");
        String cfo = mandate("Chief Financial Officer");
        String fatima = add(cfo, """
                {"fullName":"Fatima Al Mazrouei"}""").get("personId").asText();
        String rajesh = add(cfo, """
                {"fullName":"Rajesh Menon"}""").get("personId").asText();

        JsonNode catalog = catalog();
        assertThat(labelsOf(catalog)).contains("Open to work", "Open to relocate", "Passive");
        String openToWork = tagIdOf(catalog, "Open to work");
        String relocate = tagIdOf(catalog, "Open to relocate");
        String boardReady = body(mvc.perform(post("/api/v1/candidate-tags")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Board-ready"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        assertThat(codeOf(mvc.perform(post("/api/v1/candidate-tags")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"board-READY"}"""))
                .andExpect(status().isConflict())
                .andReturn())).isEqualTo("CANDIDATE_TAG_EXISTS");

        tag(fatima, openToWork);
        tag(fatima, relocate);
        tag(rajesh, openToWork);

        assertThat(namesOf(pool(Map.of("tag", List.of(openToWork, relocate), "tagMatch", "all"))))
                .containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("tag", List.of(openToWork, openToWork), "tagMatch", "all"))))
                .containsExactlyInAnyOrder("Fatima Al Mazrouei", "Rajesh Menon");
        assertThat(namesOf(pool(Map.of("tag", List.of(relocate), "tagMatch", "any"))))
                .containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("tag", List.of(relocate), "tagMatch", "none"))))
                .containsExactly("Rajesh Menon");
        assertThat(holdersOf(catalog(), "Open to work")).isEqualTo(2);

        mvc.perform(patch("/api/v1/candidate-tags/" + boardReady)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"retired":true}"""))
                .andExpect(status().isOk());
        assertThat(codeOf(mvc.perform(put("/api/v1/candidates/" + rajesh + "/tags/" + boardReady)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andReturn())).isEqualTo("CANDIDATE_TAG_RETIRED");
        assertThat(codeOf(mvc.perform(post("/api/v1/candidate-tags")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Board-Ready"}"""))
                .andExpect(status().isConflict())
                .andReturn())).isEqualTo("CANDIDATE_TAG_RETIRED");

        JsonNode untagged = body(mvc.perform(delete("/api/v1/candidates/" + fatima + "/tags/" + relocate)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(untagged.get("tagIds")).hasSize(1);
        assertThat(kindsOf(timeline(fatima, "tags"))).containsExactly("UNTAGGED", "TAGGED", "TAGGED");
    }

    @Test
    @DisplayName("a rename reaches every person and their timeline, and only an admin renames")
    void onlyAnAdminRenamesATag() throws Exception {
        firm("Tag Rename Firm");
        String cfo = mandate("Chief Financial Officer");
        String person = add(cfo, """
                {"fullName":"Noura Al Thani"}""").get("personId").asText();
        String passive = tagIdOf(catalog(), "Passive");
        tag(person, passive);
        String sara = member("sara");

        mvc.perform(patch("/api/v1/candidate-tags/" + passive)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Quiet"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/candidate-tags/" + passive)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Not looking","colour":"violet"}"""))
                .andExpect(status().isOk());

        JsonNode line = timeline(person, "tags").get(0);
        assertThat(line.get("details").get("tag").asText()).isEqualTo("Not looking");
        assertThat(labelsOf(catalog())).contains("Not looking").doesNotContain("Passive");
    }

    @Test
    @DisplayName("a person's photo is served to staff by person, and to nobody else")
    void aPhotoIsServedByPerson() throws Exception {
        firm("Pool Photo Firm");
        String cfo = mandate("Chief Financial Officer");
        String person = add(cfo, """
                {"fullName":"Mariam Saeed"}""").get("personId").asText();
        String client = clientOn(cfo);

        mvc.perform(get("/api/v1/candidates/" + person + "/photo").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        db.update("insert into app_lm_person_photo (person_id, content, content_type) values (?::uuid, ?, ?)",
                person, jpeg, "image/jpeg");
        byte[] served = mvc.perform(get("/api/v1/candidates/" + person + "/photo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(jpeg);

        mvc.perform(get("/api/v1/candidates/" + person + "/photo").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        String strangerAddress = "stranger@other-" + domain;
        createWorkspace(verifiedUser("Stranger", strangerAddress), "Pool Photo Stranger Firm");
        mvc.perform(get("/api/v1/candidates/" + person + "/photo")
                        .header("Authorization", "Bearer " + login(strangerAddress)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an owner is a colleague, filters the pool, and is a timeline line")
    void anOwnerIsAColleague() throws Exception {
        firm("Pool Owner Firm");
        String cfo = mandate("Chief Financial Officer");
        String person = add(cfo, """
                {"fullName":"Khalid Al Harbi"}""").get("personId").asText();
        add(cfo, """
                {"fullName":"Omar Farouk"}""");
        String client = clientOn(cfo);
        String clientUserId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class,
                "client@pool-client-" + domain);

        assertThat(codeOf(mvc.perform(put("/api/v1/candidates/" + person + "/owner")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerUserId":"%s"}""".formatted(clientUserId)))
                .andExpect(status().isBadRequest())
                .andReturn())).isEqualTo("PERSON_OWNER_NOT_STAFF");

        JsonNode owned = body(mvc.perform(put("/api/v1/candidates/" + person + "/owner")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerUserId":"%s"}""".formatted(adminId)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(owned.get("ownerUserId").asText()).isEqualTo(adminId);
        assertThat(namesOf(pool(Map.of("view", "mine")))).containsExactly("Khalid Al Harbi");
        assertThat(namesOf(pool(Map.of("owner", "nobody")))).containsExactly("Omar Farouk");
        JsonNode line = timeline(person, "tags").get(0);
        assertThat(line.get("kind").asText()).isEqualTo("OWNER_CHANGED");
        assertThat(line.get("details").get("owner").asText()).isEqualTo("Alok Kumar");

        mvc.perform(get("/api/v1/candidates").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidate-tags").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/candidates/" + person + "/owner")
                        .header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("do not contact is kept with who set it and why, and cleared with nothing left behind")
    void doNotContactIsRecorded() throws Exception {
        firm("Pool DNC Firm");
        String cfo = mandate("Chief Financial Officer");
        String person = add(cfo, """
                {"fullName":"Lina Said"}""").get("personId").asText();

        JsonNode marked = body(mvc.perform(put("/api/v1/candidates/" + person + "/do-not-contact")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doNotContact":true,"reason":"Asked us not to approach them."}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(marked.get("doNotContact").get("reason").asText()).isEqualTo("Asked us not to approach them.");
        assertThat(marked.get("doNotContact").get("setByName").asText()).isEqualTo("Alok Kumar");
        assertThat(rowNamed(pool(Map.of()), "Lina Said").get("doNotContact").asBoolean()).isTrue();

        JsonNode cleared = body(mvc.perform(put("/api/v1/candidates/" + person + "/do-not-contact")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doNotContact":false}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(cleared.get("doNotContact").isNull()).isTrue();
        assertThat(kindsOf(timeline(person, "contacts")))
                .containsExactly("DO_NOT_CONTACT_CLEARED", "DO_NOT_CONTACT_SET");
        assertThat(db.queryForObject("select do_not_contact_reason from app_lm_person where id = ?::uuid",
                String.class, person)).isNull();
    }

    @Test
    @DisplayName("people are added to a position as Identified, by someone seated on it, and never twice")
    void peopleAreAddedToAPosition() throws Exception {
        firm("Pool Map Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String fatima = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","employerName":"Aldar Properties"}""").get("personId").asText();
        String rajesh = add(credit, """
                {"fullName":"Rajesh Menon"}""").get("personId").asText();
        String sara = researcherOn(cfo);

        mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s"]}""".formatted(credit, fatima)))
                .andExpect(status().isForbidden());

        JsonNode answer = body(mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s","%s"]}""".formatted(credit, fatima, rajesh)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(answer.get("added").asInt()).isEqualTo(1);
        assertThat(answer.get("alreadyIn").asInt()).isEqualTo(1);

        JsonNode onCredit = body(mvc.perform(get("/api/v1/projects/" + credit + "/candidates")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("candidates");
        JsonNode added = null;
        for (JsonNode row : onCredit) {
            if (row.get("fullName").asText().equals("Fatima Al Mazrouei")) {
                added = row;
            }
        }
        assertThat(added).isNotNull();
        assertThat(added.get("status").asText()).isEqualTo("identified");
        assertThat(added.get("companyName").asText()).isEqualTo("Aldar Properties");
        assertThat(kindsOf(timeline(fatima, "positions"))).containsExactly("MAPPED", "ADDED_TO_POOL");
        assertThat(db.queryForObject("""
                select metadata ->> 'personIds' from app_lm_audit_event
                where event_type = 'PEOPLE_MAPPED_FROM_POOL'""", String.class)).contains(fatima).doesNotContain(rajesh);

        JsonNode record = body(mvc.perform(get("/api/v1/candidates/" + fatima).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(record.get("positions")).hasSize(2);
        assertThat(record.get("positions").get(0).get("positionTitle").asText()).isEqualTo("Head of Credit Risk");
        assertThat(record.get("positions").get(0).get("workable").asBoolean()).isFalse();
        assertThat(record.get("positions").get(1).get("workable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("bulk tagging and owning change only those who were not already that way")
    void bulkChangesCountWhatChanged() throws Exception {
        firm("Pool Bulk Firm");
        String cfo = mandate("Chief Financial Officer");
        String fatima = add(cfo, """
                {"fullName":"Fatima Al Mazrouei"}""").get("personId").asText();
        String rajesh = add(cfo, """
                {"fullName":"Rajesh Menon"}""").get("personId").asText();
        String referral = tagIdOf(catalog(), "Referral");
        tag(fatima, referral);

        JsonNode tagged = body(mvc.perform(post("/api/v1/candidates/bulk/tags")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personIds":["%s","%s"],"tagIds":["%s"]}""".formatted(fatima, rajesh, referral)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(tagged.get("changed").asInt()).isEqualTo(1);

        JsonNode owned = body(mvc.perform(post("/api/v1/candidates/bulk/owner")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personIds":["%s","%s"],"ownerUserId":"%s"}""".formatted(fatima, rajesh, adminId)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(owned.get("changed").asInt()).isEqualTo(2);
        assertThat(pool(Map.of("view", "mine")).get("totalCount").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("an export carries the view or the people ticked, and is audited")
    void anExportIsAudited() throws Exception {
        firm("Pool Export Firm");
        String cfo = mandate("Chief Financial Officer");
        String fatima = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","emails":[{"value":"fatima@aldar.example"}]}""")
                .get("personId").asText();
        add(cfo, """
                {"fullName":"=HYPERLINK(\\"x\\")"}""");

        String all = mvc.perform(get("/api/v1/candidates/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(all).contains("Fatima Al Mazrouei", "fatima@aldar.example", "Chief Financial Officer (Identified)");
        assertThat(all).contains("'=HYPERLINK");

        String ticked = mvc.perform(post("/api/v1/candidates/export")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personIds":["%s"]}""".formatted(fatima)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ticked).contains("Fatima Al Mazrouei").doesNotContain("HYPERLINK");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'CANDIDATES_EXPORTED' and workspace_id = (
                    select workspace_id from app_lm_person where id = ?::uuid)""", Integer.class, fatima))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a filter token nobody offers is a 400, and another workspace's person or tag is not found")
    void strangersAndUnknownTokensAreRefused() throws Exception {
        firm("Pool Strangers Firm");
        String cfo = mandate("Chief Financial Officer");
        String person = add(cfo, """
                {"fullName":"Yousef Haddad"}""").get("personId").asText();

        mvc.perform(get("/api/v1/candidates").param("view", "favourites").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates").param("owner", "someone").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates").param("direction", "sideways").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        String outsiderEmail = "nadia@other-" + domain;
        createWorkspace(verifiedUser("Nadia Rahman", outsiderEmail), "Other Pool Firm");
        String outsider = login(outsiderEmail);
        String theirTag = tagIdOf(body(mvc.perform(get("/api/v1/candidate-tags")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isOk())
                .andReturn()), "Referral");

        mvc.perform(put("/api/v1/candidates/" + person + "/tags/" + theirTag).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/candidates/" + person + "/owner")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        assertThat(pool(Map.of(), outsider).get("poolSize").asLong()).isZero();
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
        adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class, address);
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

    private String member(String name) throws Exception {
        String address = name + "@" + domain;
        inviteAndAccept(admin, name, address, "MEMBER");
        return login(address);
    }

    private String researcherOn(String projectId) throws Exception {
        String address = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", address, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, address))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(address);
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@pool-client-" + domain;
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

    private void tag(String personId, String tagId) throws Exception {
        mvc.perform(put("/api/v1/candidates/" + personId + "/tags/" + tagId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private JsonNode catalog() throws Exception {
        return body(mvc.perform(get("/api/v1/candidate-tags").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode pool(Map<String, Object> params) throws Exception {
        return pool(params, admin);
    }

    private JsonNode pool(Map<String, Object> params, String token) throws Exception {
        var request = get("/api/v1/candidates").header("Authorization", "Bearer " + token);
        for (Map.Entry<String, Object> param : params.entrySet()) {
            if (param.getValue() instanceof List<?> values) {
                for (Object value : values) {
                    request = request.param(param.getKey(), value.toString());
                }
            } else {
                request = request.param(param.getKey(), param.getValue().toString());
            }
        }
        return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private JsonNode timeline(String personId, String group) throws Exception {
        return body(mvc.perform(get("/api/v1/candidates/" + personId + "/timeline").param("group", group)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("entries");
    }

    private static JsonNode rowNamed(JsonNode page, String name) {
        for (JsonNode row : page.get("people")) {
            if (row.get("fullName").asText().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("No row named " + name);
    }

    private static List<String> namesOf(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("people").forEach(row -> names.add(row.get("fullName").asText()));
        return names;
    }

    private static List<String> labelsOf(JsonNode catalog) {
        List<String> labels = new ArrayList<>();
        catalog.forEach(tag -> labels.add(tag.get("label").asText()));
        return labels;
    }

    private static String tagIdOf(JsonNode catalog, String label) {
        for (JsonNode tag : catalog) {
            if (tag.get("label").asText().equals(label)) {
                return tag.get("id").asText();
            }
        }
        throw new AssertionError("No tag " + label);
    }

    private static long holdersOf(JsonNode catalog, String label) {
        for (JsonNode tag : catalog) {
            if (tag.get("label").asText().equals(label)) {
                return tag.get("holders").asLong();
            }
        }
        throw new AssertionError("No tag " + label);
    }

    private static List<String> kindsOf(JsonNode entries) {
        List<String> kinds = new ArrayList<>();
        entries.forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }
}

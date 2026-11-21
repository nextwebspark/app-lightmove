package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
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

    @Autowired @FixtureJdbc JdbcTemplate db;

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
    @DisplayName("every column filters the pool, its values any-of, and a researched employer is searched as it is drawn")
    void everyColumnFiltersThePool() throws Exception {
        firm("Pool Columns Firm");
        String cfo = mandate("Chief Financial Officer");
        String fatima = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","title":"Group CFO","employerName":"Aldar Properties",
                 "seniority":"C-Suite","yearsExperience":18,"locationCountry":"United Arab Emirates",
                 "linkedinUrl":"https://www.linkedin.com/in/fatima-al-mazrouei",
                 "emails":[{"value":"fatima@aldar.example"}]}""").get("personId").asText();
        String rajesh = add(cfo, """
                {"fullName":"Rajesh Menon","title":"Finance Director","yearsExperience":7,
                 "locationCountry":"Korea, Republic of","phone":"+971500000001"}""").get("personId").asText();
        add(cfo, """
                {"fullName":"Omar Haddad","title":"Controller"}""");
        db.update("""
                update app_lm_person set profile = jsonb_set(profile, '{career}',
                    '[{"title":"Finance Director"},{"company":"Mubadala","title":"Finance Director"}]'::jsonb)
                where id = ?::uuid""", rajesh);
        db.update("update app_lm_person set created_at = now() - interval '200 days' where id = ?::uuid", rajesh);

        assertThat(rowNamed(pool(Map.of()), "Rajesh Menon").get("companyName").asText()).isEqualTo("Mubadala");
        assertThat(namesOf(pool(Map.of("company", "mubadala")))).containsExactly("Rajesh Menon");
        assertThat(namesOf(pool(Map.of("q", "mubadala")))).containsExactly("Rajesh Menon");
        assertThat(namesOf(pool(Map.of("name", "fati")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("title", "controller")))).containsExactly("Omar Haddad");

        assertThat(namesOf(pool(Map.of("country", "Korea, Republic of")))).containsExactly("Rajesh Menon");
        assertThat(namesOf(pool(Map.of("country", List.of("korea, republic of", "United Arab Emirates")))))
                .containsExactlyInAnyOrder("Fatima Al Mazrouei", "Rajesh Menon");
        assertThat(namesOf(pool(Map.of("experience", List.of("5to10", "unknown")))))
                .containsExactlyInAnyOrder("Rajesh Menon", "Omar Haddad");
        assertThat(namesOf(pool(Map.of("experience", "15to20")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("seniority", List.of("C-Suite"))))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("seniority", List.of("none")))))
                .containsExactlyInAnyOrder("Rajesh Menon", "Omar Haddad");
        assertThat(namesOf(pool(Map.of("linkedin", "yes")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("contact", "none")))).containsExactly("Omar Haddad");
        assertThat(namesOf(pool(Map.of("contact", List.of("email", "phone")))))
                .containsExactlyInAnyOrder("Fatima Al Mazrouei", "Rajesh Menon");
        assertThat(namesOf(pool(Map.of("source", "manual")))).hasSize(3);
        assertThat(namesOf(pool(Map.of("added", "older")))).containsExactly("Rajesh Menon");
        assertThat(namesOf(pool(Map.of("added", "week"))))
                .containsExactlyInAnyOrder("Fatima Al Mazrouei", "Omar Haddad");
        assertThat(namesOf(pool(Map.of("activity", "week")))).hasSize(3);
        assertThat(namesOf(pool(Map.of("activity", "never")))).isEmpty();
        assertThat(namesOf(pool(Map.of("owner", List.of("nobody", adminId))))).hasSize(3);
        db.update("""
                update app_lm_project_candidate set status_id = (
                    select s.id from app_lm_workspace_candidate_status s
                    join app_lm_project p on p.workspace_id = s.workspace_id
                    where p.id = ?::uuid and s.key = 'interested')
                where person_id = ?::uuid""", cfo, fatima);
        assertThat(namesOf(pool(Map.of("pipelineStage", "interested")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("status", "identified"))))
                .containsExactlyInAnyOrder("Rajesh Menon", "Omar Haddad");
        // One Status filter: a status or a stage, never a status that must also sit in the stage.
        assertThat(namesOf(pool(Map.of("position", List.of(cfo), "status", List.of("identified"),
                "pipelineStage", List.of("interested"))))).hasSize(3);

        mvc.perform(put("/api/v1/candidates/" + fatima + "/do-not-contact")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"doNotContact":true}"""))
                .andExpect(status().isOk());
        assertThat(namesOf(pool(Map.of("doNotContact", "yes")))).containsExactly("Fatima Al Mazrouei");
        assertThat(namesOf(pool(Map.of("doNotContact", "no")))).hasSize(2);

        JsonNode fatimaRow = rowNamed(pool(Map.of()), "Fatima Al Mazrouei");
        assertThat(fatimaRow.get("seniority").asText()).isEqualTo("C-Suite");
        assertThat(fatimaRow.get("source").asText()).isEqualTo("manual");
        assertThat(fatimaRow.get("createdAt").asText()).isNotBlank();
        assertThat(namesOf(pool(Map.of("sort", "company", "direction", "asc"))))
                .containsExactly("Fatima Al Mazrouei", "Rajesh Menon", "Omar Haddad");
        assertThat(namesOf(pool(Map.of("sort", "experience", "direction", "desc"))))
                .containsExactly("Fatima Al Mazrouei", "Rajesh Menon", "Omar Haddad");
        assertThat(namesOf(pool(Map.of("sort", "added", "direction", "asc"))).getFirst()).isEqualTo("Rajesh Menon");

        mvc.perform(get("/api/v1/candidates").param("experience", "lots").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates").param("added", "never").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates").param("position", "nope").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
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
        assertThat(added.get("triageCompanyId").isNull()).isFalse();
        assertThat(kindsOf(timeline(fatima, "positions"))).containsExactly("MAPPED", "ADDED_TO_POOL");
        // The suite shares one database: read this firm's line alone, or another test's makes three.
        assertThat(db.queryForObject("""
                select metadata ->> 'personIds' from app_lm_audit_event
                where event_type = 'PEOPLE_MAPPED_FROM_POOL'
                  and workspace_id = (select workspace_id from app_lm_project where id = ?::uuid)""",
                String.class, credit)).contains(fatima).doesNotContain(rajesh);

        JsonNode record = body(mvc.perform(get("/api/v1/candidates/" + fatima).header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(record.get("positions")).hasSize(2);
        // A position the caller is not on is listed, but not named.
        assertThat(record.get("positions").get(0).get("positionTitle").isNull()).isTrue();
        assertThat(record.get("positions").get(0).get("workable").asBoolean()).isFalse();
        assertThat(record.get("positions").get(1).get("positionTitle").isNull()).isFalse();

        // Nor is it named anywhere else the pool reads: the row's last activity, the person's history, the feed.
        JsonNode saraPool = body(mvc.perform(get("/api/v1/candidates").header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(rowNamed(saraPool, "Fatima Al Mazrouei").at("/lastActivity/projectTitle").isNull()).isTrue();
        JsonNode saraHistory = body(mvc.perform(get("/api/v1/candidates/" + fatima + "/timeline")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isOk())
                .andReturn()).get("entries");
        saraHistory.forEach(line -> assertThat(line.path("projectTitle").asText("")).isNotEqualTo("Head of Credit Risk"));
        mvc.perform(get("/api/v1/candidates/activity").param("position", credit).header("Authorization", "Bearer " + sara))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/candidates/activity").param("position", credit).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(record.get("positions").get(1).get("workable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("people added to a position land under their company there, one company however many share it")
    void peopleAddedToAPositionLandUnderTheirCompany() throws Exception {
        firm("Pool Employer Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String aldar = body(mvc.perform(post("/api/v1/projects/" + cfo + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Aldar Properties","companyLinkedinUrl":"https://www.linkedin.com/company/aldar"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String fatima = add(cfo, """
                {"triageCompanyId":"%s","fullName":"Fatima Al Mazrouei"}""".formatted(aldar)).get("personId").asText();
        String omar = add(cfo, """
                {"triageCompanyId":"%s","fullName":"Omar Haddad"}""".formatted(aldar)).get("personId").asText();

        mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s","%s"]}""".formatted(credit, fatima, omar)))
                .andExpect(status().isOk());

        JsonNode onCredit = body(mvc.perform(get("/api/v1/projects/" + credit + "/candidates")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("candidates");
        assertThat(onCredit).hasSize(2);
        String creditsAldar = onCredit.get(0).get("triageCompanyId").asText();
        assertThat(creditsAldar).isNotBlank().isNotEqualTo(aldar);
        assertThat(onCredit.get(1).get("triageCompanyId").asText()).isEqualTo(creditsAldar);
        assertThat(onCredit.get(0).get("companyName").asText()).isEqualTo("Aldar Properties");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_project_triage_company where project_id = ?::uuid""",
                Integer.class, credit)).isEqualTo(1);
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

    @Test
    @DisplayName("the person record carries what research vendors kept, and research is asked for again only where it can be")
    void researchKeptIsReadAndAskedForAgain() throws Exception {
        firm("Pool Research Firm");
        String cfo = mandate("Chief Financial Officer");
        String researched = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-sample"}""")
                .get("personId").asText();
        String unlinked = add(cfo, """
                {"fullName":"Rajesh Menon"}""").get("personId").asText();
        db.update("""
                insert into app_lm_person_vendor_record (person_id, workspace_id, provider, record, details, vendor_seen_at)
                select id, workspace_id, 'brightdata', '{"followers":1200}'::jsonb,
                       '{"followers":1200,"honorsAndAwards":[{"title":"CFO of the Year"}]}'::jsonb,
                       '2026-11-01T00:00:00Z'::timestamptz
                from app_lm_person where id = ?::uuid""", researched);

        JsonNode record = body(mvc.perform(get("/api/v1/candidates/" + researched)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(record.get("research").get("followers").asLong()).isEqualTo(1200);
        assertThat(record.get("research").get("honorsAndAwards").get(0).get("title").asText())
                .isEqualTo("CFO of the Year");
        assertThat(record.get("researchSeenAt").asText()).startsWith("2026-11-01");
        assertThat(body(mvc.perform(get("/api/v1/candidates/" + unlinked).header("Authorization", "Bearer " + admin))
                .andReturn()).get("research").isNull()).isTrue();

        assertThat(body(mvc.perform(post("/api/v1/candidates/bulk/research")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personIds":["%s","%s"]}""".formatted(researched, unlinked)))
                .andExpect(status().isAccepted())
                .andReturn()).get("changed").asInt()).isEqualTo(1);
        assertThat(body(mvc.perform(post("/api/v1/candidates/" + researched + "/research")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andReturn()).get("code").asString()).isEqualTo("PERSON_RESEARCH_REFRESHING");
        assertThat(body(mvc.perform(post("/api/v1/candidates/" + unlinked + "/research")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnprocessableContent())
                .andReturn()).get("code").asString()).isEqualTo("PERSON_RESEARCH_NEEDS_PROFILE");

        String client = clientOn(cfo);
        mvc.perform(post("/api/v1/candidates/" + researched + "/research").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
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
                                {"token":"%s","fullName":"A Client","password":"%s","termsAccepted":true}
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

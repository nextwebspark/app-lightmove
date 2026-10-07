package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.StubChatModel;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Sequences and Add to sequence: who is skipped and why, the reviewed first email frozen per person,
 * the AI opener's allowlist, and a client seat refused on every route. Nothing here sends.
 */
@IntegrationTest
class OutreachSequenceIntegrationTest extends FlowTestSupport {

    private static final String CLIENT_NAME = "Acme Holdings";
    private static final String STEP_ONE_BODY = "Hi {{firstName}},\n\n{{opener}}\n\nAs {{currentTitle}} at "
            + "{{currentCompany}}, you may know someone for our {{positionTitle}} search.\n\n{{senderFirstName}}";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private StubChatModel chat;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String clientId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        chat.reset();
        String consultantEmail = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", consultantEmail), "Meridian Search");
        consultant = login(consultantEmail);
        projectId = mandate();
    }

    @AfterEach
    void resetChat() {
        chat.reset();
    }

    @Test
    @DisplayName("a sequence is written whole, at most three emails, and its first email needs a subject")
    void writeASequence() throws Exception {
        String sequenceId = createSequence("CFO — first approach");

        JsonNode read = body(as(consultant, get(outreach("/sequences/" + sequenceId))).andExpect(status().isOk()).andReturn());
        assertThat(read.get("steps")).hasSize(3);
        assertThat(read.get("steps").get(0).get("delayWorkingDays").asInt()).isZero();
        assertThat(read.get("steps").get(1).get("subject").isNull()).isTrue();
        assertThat(read.get("createdByName").asText()).isEqualTo("Yara Haddad");

        as(consultant, put(outreach("/sequences/" + sequenceId)).contentType(MediaType.APPLICATION_JSON)
                .content(sequenceJson("Renamed", 4)))
                .andExpect(status().isBadRequest());
        as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"No subject","steps":[{"delayWorkingDays":0,"subject":"","body":"Hi"}]}"""))
                .andExpect(status().isBadRequest());

        as(consultant, delete(outreach("/sequences/" + sequenceId))).andExpect(status().isNoContent());
        JsonNode list = body(as(consultant, get(outreach("/sequences"))).andReturn());
        assertThat(list.get("sequences")).isEmpty();
    }

    @Test
    @DisplayName("a sequence keeps its own sending days and hours, and a follow-up's time must fall inside them")
    void aSequenceKeepsItsOwnSchedule() throws Exception {
        String sequenceId = createSequence("Default week");
        JsonNode defaults = body(as(consultant, get(outreach("/sequences/" + sequenceId))).andReturn()).get("schedule");
        assertThat(defaults.get("days").toString())
                .isEqualTo("[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]");
        assertThat(defaults.get("windowStart").asText()).startsWith("08:00");

        JsonNode saved = body(as(consultant, put(outreach("/sequences/" + sequenceId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(scheduledSequenceJson("Gulf week", "09:00", "17:00", "\"09:30\"")))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(saved.get("schedule").get("days").toString())
                .isEqualTo("[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"SUNDAY\"]");
        assertThat(saved.get("schedule").get("windowEnd").asText()).startsWith("17:00");
        assertThat(saved.get("steps").get(1).get("sendTime").asText()).startsWith("09:30");
        assertThat(saved.get("steps").get(0).get("sendTime").isNull()).isTrue();

        JsonNode kept = body(as(consultant, put(outreach("/sequences/" + sequenceId))
                .contentType(MediaType.APPLICATION_JSON).content(sequenceJson("Renamed", 2)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(kept.get("schedule").get("windowStart").asText()).startsWith("09:00");

        as(consultant, put(outreach("/sequences/" + sequenceId)).contentType(MediaType.APPLICATION_JSON)
                .content(scheduledSequenceJson("Late", "09:00", "17:00", "\"18:30\"")))
                .andExpect(status().isBadRequest());
        as(consultant, put(outreach("/sequences/" + sequenceId)).contentType(MediaType.APPLICATION_JSON)
                .content(scheduledSequenceJson("Backwards", "17:00", "09:00", "null")))
                .andExpect(status().isBadRequest());
        as(consultant, put(outreach("/sequences/" + sequenceId)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"No days","steps":[{"delayWorkingDays":0,"subject":"Hi","body":"Hi"}],
                         "schedule":{"days":[],"windowStart":"09:00","windowEnd":"17:00"}}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Choose lists everyone at the ticked companies and gives each skipped person their reason")
    void skippedPeopleCarryTheirReason() throws Exception {
        String sequenceId = createSequence("First approach");
        String ready = executive("Priya Raman", "priya@target.example", null);
        String noEmail = executive("Omar Said", null, null);
        String doNotContact = executive("Lina Haddad", "lina@target.example", null);
        String outOfTheRunning = executive("Karim Aziz", "karim@target.example", null);
        String alreadyIn = executive("Sara Nasser", "sara@target.example", null);
        markDoNotContact(doNotContact);
        as(consultant, patch("/api/v1/projects/" + projectId + "/candidates/" + outOfTheRunning)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"notInterested\"}"))
                .andExpect(status().isOk());
        connectMailbox();
        start(sequenceId, List.of(person(alreadyIn, "sara@target.example", "An opener.", false)))
                .andExpect(status().isCreated());

        Map<String, JsonNode> chosen = choose(List.of(ready, noEmail, doNotContact, outOfTheRunning, alreadyIn));

        assertThat(chosen.get(ready).get("skipReason").isNull()).isTrue();
        assertThat(chosen.get(noEmail).get("skipReason").asText()).isEqualTo("NO_EMAIL");
        assertThat(chosen.get(doNotContact).get("skipReason").asText()).isEqualTo("DO_NOT_CONTACT");
        assertThat(chosen.get(outOfTheRunning).get("skipReason").asText()).isEqualTo("LEFT_THE_RUNNING");
        assertThat(chosen.get(alreadyIn).get("skipReason").asText()).isEqualTo("ALREADY_IN_SEQUENCE");
        assertThat(chosen.get(alreadyIn).get("inSequence").asText()).isEqualTo("First approach");
        assertThat(chosen.get(ready).get("tokens").get("firstName").asText()).isEqualTo("Priya");
        assertThat(chosen.get(ready).get("tokens").get("senderFirstName").asText()).isEqualTo("Yara");
    }

    @Test
    @DisplayName("a skipped person is never enrolled, even when the request names them")
    void startRefusesSkippedPeople() throws Exception {
        String sequenceId = createSequence("First approach");
        String ready = executive("Priya Raman", "priya@target.example", null);
        String doNotContact = executive("Lina Haddad", "lina@target.example", null);
        markDoNotContact(doNotContact);
        connectMailbox();

        MvcResult refused = start(sequenceId, List.of(person(ready, "priya@target.example", "One.", false),
                person(doNotContact, "lina@target.example", "Two.", false)))
                .andExpect(status().isConflict())
                .andReturn();

        assertThat(codeOf(refused)).isEqualTo("OUTREACH_PERSON_SKIPPED");
        assertThat(enrollmentCount()).isZero();
    }

    @Test
    @DisplayName("Start schedules each person with their own reviewed email, a timeline line, and sends nothing")
    void startFreezesEachPersonsOwnEmail() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@target.example", null);
        String rajesh = executive("Rajesh Menon", "rajesh@target.example", null);
        connectMailbox();

        start(sequenceId, List.of(person(priya, "Priya@Target.example", "Your move into treasury stood out.", true),
                person(rajesh, "rajesh@target.example", "Your Gulf expansion caught my eye.", false)))
                .andExpect(status().isCreated());

        Map<String, Map<String, Object>> rows = new HashMap<>();
        jdbc.queryForList("select candidate_id::text as candidate, status, to_address, first_subject, first_body, "
                        + "opener_edited from app_lm_outreach_enrollment where project_id = ?::uuid", projectId)
                .forEach(row -> rows.put((String) row.get("candidate"), row));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(priya).get("status")).isEqualTo("SCHEDULED");
        assertThat(rows.get(priya).get("to_address")).isEqualTo("priya@target.example");
        assertThat(rows.get(priya).get("first_subject")).isEqualTo("Confidential: Group CFO");
        assertThat((String) rows.get(priya).get("first_body"))
                .startsWith("Hi Priya,\n\nYour move into treasury stood out.")
                .contains("for our Group CFO search")
                .endsWith("Yara")
                .doesNotContain("Gulf expansion");
        assertThat((String) rows.get(rajesh).get("first_body"))
                .contains("Your Gulf expansion caught my eye.")
                .doesNotContain("treasury");
        assertThat(rows.get(priya).get("opener_edited")).isEqualTo(true);
        assertThat(gateway.sent()).isEmpty();

        Integer lines = jdbc.queryForObject("select count(*) from app_lm_person_activity "
                + "where project_id = ?::uuid and kind = 'OUTREACH_ENROLLED'", Integer.class, projectId);
        assertThat(lines).isEqualTo(2);
    }

    @Test
    @DisplayName("the To address must be one the person's ledger holds, and a mailbox must be connected")
    void startNeedsALedgerAddressAndAMailbox() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@target.example", null);

        assertThat(codeOf(start(sequenceId, List.of(person(priya, "priya@target.example", null, false)))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("MAILBOX_NOT_CONNECTED");

        connectMailbox();
        assertThat(codeOf(start(sequenceId, List.of(person(priya, "someone@else.example", null, false)))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("OUTREACH_ADDRESS_NOT_ON_FILE");
        assertThat(enrollmentCount()).isZero();
    }

    @Test
    @DisplayName("the opener prompt never carries an email, phone, package, note or the client's name")
    void theOpenerSeesOnlyTheAllowlist() throws Exception {
        String priya = executive("Priya Raman", "priya.private@target.example", """
                ,"phone":"+971 50 123 4567","note":"Confided she is unhappy with her board",
                "compensation":{"currency":"AED","baseSalary":987654}""");
        chat.answerWhenSystemContains("headhunter's first, cold email",
                "{\"opener\":\"Your move from audit into treasury stood out.\"}");

        JsonNode drafted = body(as(consultant, post(outreach("/openers")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[\"%s\"]}".formatted(priya)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(drafted.get("openers").get(0).get("opener").asText())
                .isEqualTo("Your move from audit into treasury stood out.");
        String prompt = chat.lastPrompt().getContents();
        assertThat(prompt)
                .contains("Priya Raman", "Group CFO")
                .doesNotContain("priya.private@target.example", "123 4567", "987654", "unhappy with her board",
                        CLIENT_NAME);
        assertThat(jdbc.queryForList("SELECT units FROM app_lm_usage_event WHERE project_id = ?::uuid AND kind = ?",
                Integer.class, projectId, "OUTREACH_OPENER")).containsExactly(1);
    }

    @Test
    @DisplayName("no opener is drafted, and nothing spent, for someone who may not be approached")
    void noOpenerForSomeoneSkipped() throws Exception {
        String doNotContact = executive("Lina Haddad", "lina@target.example", null);
        String noEmail = executive("Omar Said", null, null);
        markDoNotContact(doNotContact);

        for (String skipped : List.of(doNotContact, noEmail)) {
            MvcResult refused = as(consultant, post(outreach("/openers")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"candidateIds\":[\"%s\"]}".formatted(skipped)))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertThat(codeOf(refused)).isEqualTo("OUTREACH_PERSON_SKIPPED");
        }
        assertThat(chat.prompts()).isEmpty();
    }

    @Test
    @DisplayName("a long subject renders in full rather than failing as someone already enrolled")
    void aLongRenderedSubjectIsKept() throws Exception {
        String sequenceId = body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Long subject","steps":[{"delayWorkingDays":0,
                         "subject":"{{currentTitle}} / {{currentTitle}}","body":"Hi"}]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String longTitle = "Group Chief Financial Officer and Head of Strategy, Treasury, Investor Relations, "
                + "Mergers and Acquisitions, Procurement and Shared Services for the Gulf region";
        String priya = executive("Priya Raman", "priya@target.example", ",\"title\":\"%s\"".formatted(longTitle));
        connectMailbox();

        start(sequenceId, List.of(person(priya, "priya@target.example", null, false))).andExpect(status().isCreated());

        String stored = jdbc.queryForObject("select first_subject from app_lm_outreach_enrollment where project_id = ?::uuid",
                String.class, projectId);
        assertThat(stored).isEqualTo(longTitle + " / " + longTitle).hasSizeGreaterThan(300);
    }

    @Test
    @DisplayName("a client seat is refused on every outreach route")
    void aClientSeatIsRefusedEverywhere() throws Exception {
        String sequenceId = createSequence("First approach");
        String priya = executive("Priya Raman", "priya@target.example", null);
        String rep = clientSeat();

        List<MockHttpServletRequestBuilder> routes = List.of(
                get(outreach("/sequences")),
                get(outreach("/sequences/" + sequenceId)),
                post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON).content(sequenceJson("X", 1)),
                put(outreach("/sequences/" + sequenceId)).contentType(MediaType.APPLICATION_JSON)
                        .content(sequenceJson("X", 1)),
                delete(outreach("/sequences/" + sequenceId)),
                post(outreach("/enrollment-candidates")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"%s\"]}".formatted(priya)),
                post(outreach("/openers")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateIds\":[\"%s\"]}".formatted(priya)),
                post(outreach("/sequences/" + sequenceId + "/enrollments")).contentType(MediaType.APPLICATION_JSON)
                        .content(startJson(List.of(person(priya, "priya@target.example", null, false)))));
        for (MockHttpServletRequestBuilder route : routes) {
            as(rep, route).andExpect(status().isForbidden());
        }
    }

    private Map<String, JsonNode> choose(List<String> candidateIds) throws Exception {
        List<String> quoted = candidateIds.stream().map(id -> "\"" + id + "\"").toList();
        JsonNode people = body(as(consultant, post(outreach("/enrollment-candidates"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidateIds\":[" + String.join(",", quoted) + "]}"))
                .andExpect(status().isOk())
                .andReturn()).get("people");
        Map<String, JsonNode> byCandidate = new HashMap<>();
        people.forEach(person -> byCandidate.put(person.get("candidateId").asText(), person));
        return byCandidate;
    }

    private org.springframework.test.web.servlet.ResultActions start(String sequenceId, List<String> people)
            throws Exception {
        return as(consultant, post(outreach("/sequences/" + sequenceId + "/enrollments"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(startJson(people)));
    }

    private static String startJson(List<String> people) {
        return "{\"people\":[" + String.join(",", people) + "]}";
    }

    private static String person(String candidateId, String toAddress, String opener, boolean edited) {
        return """
                {"candidateId":"%s","toAddress":"%s","opener":%s,"openerEdited":%s}"""
                .formatted(candidateId, toAddress, opener == null ? "null" : "\"" + opener + "\"", edited);
    }

    private String createSequence(String name) throws Exception {
        return body(as(consultant, post(outreach("/sequences")).contentType(MediaType.APPLICATION_JSON)
                .content(sequenceJson(name, 3)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private static String sequenceJson(String name, int steps) {
        List<String> written = new ArrayList<>();
        written.add("""
                {"delayWorkingDays":0,"subject":"Confidential: {{positionTitle}}","body":"%s"}"""
                .formatted(STEP_ONE_BODY.replace("\n", "\\n")));
        for (int step = 1; step < steps; step++) {
            written.add("""
                    {"delayWorkingDays":3,"subject":"ignored","body":"Following up, {{firstName}}."}""");
        }
        return "{\"name\":\"%s\",\"steps\":[%s]}".formatted(name, String.join(",", written));
    }

    private static String scheduledSequenceJson(String name, String windowStart, String windowEnd,
                                                String followUpSendTime) {
        return """
                {"name":"%s","steps":[
                  {"delayWorkingDays":0,"subject":"Confidential","body":"Hi {{firstName}}"},
                  {"delayWorkingDays":2,"body":"Following up.","sendTime":%s}],
                 "schedule":{"days":["SUNDAY","MONDAY","TUESDAY","WEDNESDAY","THURSDAY"],
                             "windowStart":"%s","windowEnd":"%s"}}"""
                .formatted(name, followUpSendTime, windowStart, windowEnd);
    }

    private String executive(String fullName, String emailAddress, String extraJson) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + System.nanoTime();
        String emailField = emailAddress == null ? "" : ",\"email\":\"%s\"".formatted(emailAddress);
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s"%s%s}
                        """.formatted(fullName, slug, emailField, extraJson == null ? "" : extraJson)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void markDoNotContact(String candidateId) throws Exception {
        String personId = body(as(consultant, get("/api/v1/projects/" + projectId + "/candidates/" + candidateId))
                .andReturn()).get("personId").asText();
        as(consultant, put("/api/v1/candidates/" + personId + "/do-not-contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doNotContact\":true,\"reason\":\"Asked not to be approached.\"}"))
                .andExpect(status().isOk());
    }

    private String mandate() throws Exception {
        clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"%s\"}".formatted(CLIENT_NAME)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** A hiring-company representative seated on this position: the CLIENT seat grants WORK_VIEW only. */
    private String clientSeat() throws Exception {
        String repEmail = "rep@client-" + domain;
        String representativeId = body(as(consultant, post("/api/v1/clients/" + clientId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Rep Person","position":"Advisor","email":"%s"}""".formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Rep Person","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        as(consultant, post("/api/v1/projects/" + projectId + "/representatives")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"representativeId\":\"%s\"}".formatted(representativeId)))
                .andExpect(status().is2xxSuccessful());
        as(rep, get("/api/v1/projects/" + projectId + "/candidates")).andExpect(status().isOk());
        return rep;
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

    private int enrollmentCount() {
        Integer count = jdbc.queryForObject("select count(*) from app_lm_outreach_enrollment where project_id = ?::uuid",
                Integer.class, projectId);
        return count == null ? 0 : count;
    }

    private org.springframework.test.web.servlet.ResultActions as(String token, MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String outreach(String path) {
        return "/api/v1/projects/" + projectId + "/outreach" + path;
    }
}

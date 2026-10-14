package app.lightmove.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.PipelineFlowSupport;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.notification.service.NotificationEmails;
import app.lightmove.api.outreach.model.OutreachReplied;
import app.lightmove.api.task.service.TaskDueScanner;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The bell (V133): an assignment, a mention, a due date, outreach replies folded into one row — each only for
 * someone who works the position, a note never quoted — and the email that follows only what stays unread.
 */
@IntegrationTest
class NotificationIntegrationTest extends PipelineFlowSupport {

    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private NotificationEmails emails;
    @Autowired private TaskDueScanner dueScanner;

    private String projectId;
    private String researcher;
    private String researcherId;
    private String researcherAddress;
    private String layla;

    @BeforeEach
    void aPositionWithATeam() throws Exception {
        firm("Bell Firm");
        projectId = mandate("Chief Financial Officer");
        researcher = researcherOn(projectId, "sara");
        researcherAddress = "sara@" + domain;
        researcherId = userIdOf(researcher);
        layla = add(projectId, """
                {"fullName":"Layla Haddad","status":"contacted"}""").get("id").asText();
    }

    @Test
    @DisplayName("an assignment rings the assignee's bell, never the bell of whoever put it on themselves")
    void anAssignmentRingsTheAssignee() throws Exception {
        assign(researcherId, "Reference check", Instant.now().plus(2, ChronoUnit.DAYS));
        assign(userIdOf(admin), "My own", Instant.now().plus(2, ChronoUnit.DAYS));

        List<JsonNode> seated = rowsOfKind(researcher, "added_to_team");
        assertThat(seated).hasSize(1);
        assertThat(seated.getFirst().at("/actor/fullName").asText()).isEqualTo("Alok Kumar");
        JsonNode bell = read(researcher, "/api/v1/me/notifications?filter=unread");
        assertThat(bell.get("unreadCount").asInt()).isEqualTo(2);
        List<JsonNode> assigned = rowsOfKind(researcher, "task_assigned");
        assertThat(assigned).hasSize(1);
        JsonNode row = assigned.getFirst();
        assertThat(row.at("/actor/fullName").asText()).isEqualTo("Alok Kumar");
        assertThat(row.get("taskTitle").asText()).isEqualTo("Reference check");
        assertThat(row.get("personName").asText()).isEqualTo("Layla Haddad");
        assertThat(row.get("candidateId").asText()).isEqualTo(layla);
        assertThat(read(admin, "/api/v1/me/notifications?filter=unread").get("unreadCount").asInt()).isZero();

        JsonNode read = body(write(researcher, "POST", "/api/v1/me/notifications/read", """
                {"all":true}"""));
        assertThat(read.get("unreadCount").asInt()).isZero();
    }

    @Test
    @DisplayName("a mention reaches only the position's team, and the bell names the note without quoting it")
    void aMentionReachesTheTeamOnly() throws Exception {
        String outsider = member("omar");
        String clientId = userIdOf(clientOn(projectId));
        String body = "Spoke to her — @[sara](user:%s) please call; @[omar](user:%s) and @[client](user:%s) too"
                .formatted(researcherId, userIdOf(outsider), clientId);

        assertThat(write(admin, "POST", candidatesUrl(projectId) + "/" + layla + "/notes", """
                {"kind":"general","body":"%s"}""".formatted(body)).getResponse().getStatus()).isEqualTo(201);

        List<JsonNode> mentions = rowsOfKind(researcher, "mentioned");
        assertThat(mentions).hasSize(1);
        assertThat(mentions.getFirst().at("/note/kind").asText()).isEqualTo("general");
        assertThat(read(researcher, "/api/v1/me/notifications?filter=all").toString()).doesNotContain("please call");
        assertThat(rowsOfKind(outsider, "mentioned")).isEmpty();
        assertThat(db.queryForObject("select count(*) from app_lm_note_mention m join app_lm_person_note n "
                + "on n.id = m.note_id where n.workspace_id = ?::uuid", Integer.class, workspaceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a next action set from a note names it by kind and date, never its words, until it is deleted")
    void aTaskFromANotePointsBackAtIt() throws Exception {
        String notes = candidatesUrl(projectId) + "/" + layla + "/notes";
        String noteId = body(write(admin, "POST", notes, """
                {"kind":"call","body":"Wants to speak to her family first"}""")).get("id").asText();
        JsonNode task = body(write(admin, "POST", candidatesUrl(projectId) + "/" + layla + "/tasks", """
                {"title":"Call back","kind":"call","assigneeUserId":"%s","dueAt":"%s","noteId":"%s"}"""
                .formatted(researcherId, Instant.now().plus(2, ChronoUnit.DAYS), noteId)));
        assertThat(task.at("/note/id").asText()).isEqualTo(noteId);
        assertThat(task.at("/note/kind").asText()).isEqualTo("call");
        assertThat(task.at("/note/writtenAt").isNull()).isFalse();
        assertThat(task.get("source").asText()).isEqualTo("note");

        List<JsonNode> assigned = rowsOfKind(researcher, "task_assigned");
        assertThat(assigned).hasSize(1);
        assertThat(assigned.getFirst().at("/note/kind").asText()).isEqualTo("call");
        assertThat(read(researcher, "/api/v1/me/notifications?filter=all").toString()).doesNotContain("family");

        assertThat(mvc.perform(delete(notes + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        JsonNode open = read(researcher, candidatesUrl(projectId) + "/" + layla + "/tasks").get("open");
        assertThat(open.get(0).get("note").isNull()).isTrue();
        assertThat(rowsOfKind(researcher, "task_assigned").getFirst().get("note").isNull()).isTrue();
    }

    @Test
    @DisplayName("replies on one position fold into one row naming the latest people")
    void repliesFoldIntoOneRow() throws Exception {
        String karim = add(projectId, """
                {"fullName":"Karim Nassar","status":"contacted"}""").get("id").asText();
        replied(layla);
        replied(karim);

        List<JsonNode> replies = rowsOfKind(researcher, "outreach_replied");
        assertThat(replies).hasSize(1);
        assertThat(replies.getFirst().get("count").asInt()).isEqualTo(2);
        assertThat(replies.getFirst().get("names").get(0).asText()).isEqualTo("Karim Nassar");
        assertThat(replies.getFirst().get("names").get(1).asText()).isEqualTo("Layla Haddad");
    }

    @Test
    @DisplayName("an assignment still unread after the delay is emailed once; one already read never is")
    void onlyWhatStaysUnreadIsEmailed() throws Exception {
        assign(researcherId, "Request CV", Instant.now().plus(2, ChronoUnit.DAYS));
        emails.emailUnreadAt(Instant.now().plus(11, ChronoUnit.MINUTES));
        emails.emailUnreadAt(Instant.now().plus(12, ChronoUnit.MINUTES));
        assertThat(email.subjectsFor(researcherAddress)).containsOnlyOnce("Alok Kumar assigned you: Request CV");

        assign(researcherId, "Debrief after meeting", Instant.now().plus(2, ChronoUnit.DAYS));
        write(researcher, "POST", "/api/v1/me/notifications/read", """
                {"all":true}""");
        emails.emailUnreadAt(Instant.now().plus(11, ChronoUnit.MINUTES));
        assertThat(email.subjectsFor(researcherAddress)).noneMatch(subject -> subject.contains("Debrief"));
    }

    @Test
    @DisplayName("a weekday morning's digest goes once, in the person's own zone")
    void theDigestGoesOncePerMorning() throws Exception {
        assign(researcherId, "Call to qualify interest", Instant.now().minus(1, ChronoUnit.HOURS));
        ZoneId dubai = ZoneId.of("Asia/Dubai");
        Instant morning = LocalDate.now(dubai).plusDays(1).with(TemporalAdjusters.next(DayOfWeek.TUESDAY))
                .atTime(LocalTime.of(8, 5)).atZone(dubai).toInstant();

        emails.digestAt(morning);
        emails.digestAt(morning.plus(10, ChronoUnit.MINUTES));

        List<EmailMessage> digests = email.sent().stream()
                .filter(message -> message.to().equalsIgnoreCase(researcherAddress))
                .filter(message -> message.subject().startsWith("Your Uncava day"))
                .toList();
        assertThat(digests).hasSize(1);
        assertThat(digests.getFirst().textBody()).contains("Call to qualify interest");
    }

    @Test
    @DisplayName("a task falling due rings once")
    void aTaskFallingDueRingsOnce() throws Exception {
        assign(researcherId, "Schedule a call", Instant.now().plus(30, ChronoUnit.MINUTES));
        write(researcher, "POST", "/api/v1/me/notifications/read", """
                {"all":true}""");

        dueScanner.scanAt(Instant.now());
        dueScanner.scanAt(Instant.now());

        List<JsonNode> due = rowsOfKind(researcher, "task_due_soon");
        assertThat(due).hasSize(1);
        assertThat(due.getFirst().get("dueAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("a kind switched off in Settings rings no bell, and a client has no bell at all")
    void preferencesAndClients() throws Exception {
        JsonNode preferences = read(researcher, "/api/v1/me/notification-preferences");
        assertThat(preferences.get("kinds")).hasSize(8);
        assertThat(preferences.get("timeZone").asText()).isEqualTo("Asia/Dubai");
        write(researcher, "PUT", "/api/v1/me/notification-preferences", """
                {"digest":false,"kinds":[{"kind":"task_assigned","inApp":false,"email":false}]}""");

        assign(researcherId, "Re-engage later", Instant.now().plus(2, ChronoUnit.DAYS));
        assertThat(rowsOfKind(researcher, "task_assigned")).isEmpty();
        assertThat(read(researcher, "/api/v1/me/notification-preferences").get("digest").asBoolean()).isFalse();

        String client = clientOn(projectId);
        assertThat(mvc.perform(get("/api/v1/me/notifications").header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("an open bell is told at once that something rang")
    void theStreamNudgesTheBell() throws Exception {
        MvcResult stream = openStream("/api/v1/me/notifications/stream", researcher);
        assign(researcherId, "Share profile with hiring manager", Instant.now().plus(1, ChronoUnit.DAYS));
        awaitContent(stream, "\"kind\":\"notifications\"");
    }

    private void assign(String assigneeId, String title, Instant dueAt) throws Exception {
        assertThat(write(admin, "POST", candidatesUrl(projectId) + "/" + layla + "/tasks", """
                {"title":"%s","kind":"todo","assigneeUserId":"%s","dueAt":"%s"}"""
                .formatted(title, assigneeId, dueAt)).getResponse().getStatus()).isEqualTo(201);
    }

    private void replied(String candidateId) {
        UUID personId = db.queryForObject("select person_id from app_lm_project_candidate where id = ?::uuid",
                UUID.class, candidateId);
        transactions.executeWithoutResult(status -> events.publishEvent(new OutreachReplied(
                UUID.fromString(workspaceId), UUID.fromString(projectId), UUID.fromString(candidateId), personId,
                UUID.randomUUID(), UUID.fromString(researcherId))));
    }

    private JsonNode read(String token, String url) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String userIdOf(String token) throws Exception {
        return read(token, "/api/v1/auth/me").get("id").asText();
    }

    private List<JsonNode> rowsOfKind(String token, String kind) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        read(token, "/api/v1/me/notifications?filter=all").get("items").forEach(row -> {
            if (row.get("kind").asText().equals(kind)) {
                rows.add(row);
            }
        });
        return rows;
    }
}

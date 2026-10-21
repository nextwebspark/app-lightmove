package app.lightmove.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.PipelineFlowSupport;
import app.lightmove.api.outreach.model.OutreachReplied;
import app.lightmove.api.task.service.TaskDueScanner;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Tasks on a position's executives (V133): they go only to someone who works the position, a teammate leaving
 * hands theirs to the lead, My tasks reads the viewer's own days, the workspace's presets are its own, and an
 * outreach reply files one task however often it is reported. None of it is a client seat's.
 */
@IntegrationTest
class TaskIntegrationTest extends PipelineFlowSupport {

    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate db;
    @Autowired private TaskDueScanner dueScanner;

    private String projectId;
    private String researcher;
    private String researcherId;
    private String adminId;
    private String layla;

    @BeforeEach
    void aPositionWithATeam() throws Exception {
        firm("Task Firm");
        projectId = mandate("Chief Financial Officer");
        researcher = researcherOn(projectId, "sara");
        researcherId = userIdOf(researcher);
        adminId = userIdOf(admin);
        layla = add(projectId, """
                {"fullName":"Layla Haddad","status":"contacted"}""").get("id").asText();
    }

    @Test
    @DisplayName("a next action goes to someone on the team and is a line on the person's timeline")
    void aTaskGoesToTheTeam() throws Exception {
        JsonNode task = body(created(admin, layla, "Reference check", researcherId, "2026-11-02T09:00:00Z"));
        assertThat(task.at("/assignee/fullName").asText()).isEqualTo("sara");
        assertThat(task.at("/createdBy/fullName").asText()).isEqualTo("Alok Kumar");
        assertThat(task.get("status").asText()).isEqualTo("open");
        assertThat(task.get("source").asText()).isEqualTo("manual");
        assertThat(task.get("personName").asText()).isEqualTo("Layla Haddad");
        assertThat(task.get("projectTitle").asText()).isEqualTo("Chief Financial Officer");

        JsonNode tasks = read(researcher, tasksUrl(layla));
        assertThat(tasks.get("open")).hasSize(1);
        assertThat(timelineKinds()).contains("TASK_CREATED");
    }

    @Test
    @DisplayName("a task never goes to someone off the team, and a client seat reads and files none")
    void theTeamIsTheBoundary() throws Exception {
        String outsider = member("omar");
        assertThat(codeOf(created(admin, layla, "Call", userIdOf(outsider), "2026-11-02T09:00:00Z")))
                .isEqualTo("TASK_ASSIGNEE_NOT_ON_TEAM");

        String client = clientOn(projectId);
        assertThat(created(client, layla, "Call", adminId, "2026-11-02T09:00:00Z").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mvc.perform(get(tasksUrl(layla)).header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/me/tasks").header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("anyone on the team completes a task; only its creator, holder or the lead changes it")
    void completingIsTheTeamsChangingIsTheOwners() throws Exception {
        String taskId = body(created(admin, layla, "Share profile with hiring manager", adminId,
                "2026-11-02T09:00:00Z")).get("id").asText();

        assertThat(codeOf(write(researcher, "PATCH", "/api/v1/tasks/" + taskId, """
                {"title":"Something else"}"""))).isEqualTo("TASK_NOT_YOURS");
        JsonNode done = body(write(researcher, "PATCH", "/api/v1/tasks/" + taskId, """
                {"status":"done"}"""));
        assertThat(done.get("status").asText()).isEqualTo("done");
        assertThat(done.at("/completedBy/fullName").asText()).isEqualTo("sara");
        assertThat(read(admin, tasksUrl(layla)).get("done")).hasSize(1);
        assertThat(read(admin, tasksUrl(layla)).get("doneCount").asInt()).isEqualTo(1);

        JsonNode reopened = body(write(admin, "PATCH", "/api/v1/tasks/" + taskId, """
                {"status":"open","title":"Share the profile"}"""));
        assertThat(reopened.get("status").asText()).isEqualTo("open");
        assertThat(reopened.get("title").asText()).isEqualTo("Share the profile");
        assertThat(timelineKinds()).contains("TASK_COMPLETED", "TASK_REOPENED");

        assertThat(mvc.perform(delete("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + researcher))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(delete("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(read(admin, tasksUrl(layla)).get("open")).isEmpty();
        assertThat(timelineKinds()).contains("TASK_CANCELLED");
    }

    @Test
    @DisplayName("the drawer reads the last three done tasks, and every one when it asks")
    void theLastThreeDoneUnlessAllAreAsked() throws Exception {
        for (int index = 0; index < 5; index++) {
            String taskId = body(created(admin, layla, "Done one " + index, adminId, "2026-11-02T09:00:00Z"))
                    .get("id").asText();
            write(admin, "PATCH", "/api/v1/tasks/" + taskId, """
                    {"status":"done"}""");
        }
        JsonNode latest = read(admin, tasksUrl(layla));
        assertThat(latest.get("done")).hasSize(3);
        assertThat(latest.get("doneCount").asInt()).isEqualTo(5);
        assertThat(latest.get("done").get(0).get("title").asText()).isEqualTo("Done one 4");
        assertThat(read(admin, tasksUrl(layla) + "?allDone=true").get("done")).hasSize(5);
    }

    @Test
    @DisplayName("My tasks reads overdue, today and upcoming in the viewer's own day, and what they set others")
    void myTasksReadTheViewersDay() throws Exception {
        Instant now = Instant.now();
        int offsetHours = Math.clamp(12 - now.atZone(ZoneOffset.UTC).getHour(), -12, 14);
        String zone = URLEncoder.encode(ZoneOffset.ofHours(offsetHours).getId(), StandardCharsets.UTF_8);
        created(admin, layla, "Overdue one", adminId, now.minus(30, ChronoUnit.MINUTES).toString());
        created(admin, layla, "Today one", adminId, now.plus(30, ChronoUnit.MINUTES).toString());
        created(admin, layla, "Later one", adminId, now.plus(3, ChronoUnit.DAYS).toString());
        created(admin, layla, "Sara's one", researcherId, now.plus(1, ChronoUnit.DAYS).toString());

        JsonNode today = read(admin, "/api/v1/me/tasks?view=today&tz=" + zone);
        assertThat(titles(today)).containsExactly("Today one");
        assertThat(today.at("/counts/overdue").asInt()).isEqualTo(1);
        assertThat(today.at("/counts/upcoming").asInt()).isEqualTo(1);
        assertThat(today.at("/counts/assignedByMe").asInt()).isEqualTo(1);
        assertThat(titles(read(admin, "/api/v1/me/tasks?view=overdue&tz=" + zone))).containsExactly("Overdue one");
        assertThat(titles(read(admin, "/api/v1/me/tasks?view=assigned_by_me&tz=" + zone)))
                .containsExactly("Sara's one");
        assertThat(titles(read(researcher, "/api/v1/me/tasks?view=upcoming&tz=" + zone)))
                .containsExactly("Sara's one");
    }

    @Test
    @DisplayName("My tasks reads a tab a slice at a time, soonest first, and says whether more follow")
    void myTasksReadASliceAtATime() throws Exception {
        Instant now = Instant.now();
        for (int days = 4; days >= 2; days--) {
            created(admin, layla, "In " + days + " days", adminId, now.plus(days, ChronoUnit.DAYS).toString());
        }

        JsonNode first = read(admin, "/api/v1/me/tasks?view=upcoming&size=2");
        assertThat(titles(first)).containsExactly("In 2 days", "In 3 days");
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        assertThat(first.at("/counts/upcoming").asInt()).isEqualTo(3);

        JsonNode second = read(admin, "/api/v1/me/tasks?view=upcoming&size=2&offset=2");
        assertThat(titles(second)).containsExactly("In 4 days");
        assertThat(second.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("an archived position's tasks leave My tasks and the due scan, and come back when it is restored")
    void anArchivedPositionsTasksGoQuiet() throws Exception {
        Instant now = Instant.now();
        String overdueId = body(created(admin, layla, "Overdue one", adminId,
                now.minus(30, ChronoUnit.MINUTES).toString())).get("id").asText();
        created(admin, layla, "Sara's one", researcherId, now.plus(1, ChronoUnit.DAYS).toString());
        assertThat(read(admin, "/api/v1/projects/" + projectId + "/archive-preview").get("openTasks").asInt())
                .isEqualTo(2);

        assertThat(write(admin, "POST", "/api/v1/projects/" + projectId + "/archive", """
                {"confirmName":"Chief Financial Officer"}""").getResponse().getStatus()).isEqualTo(200);
        dueScanner.scanAt(now);

        JsonNode archived = read(admin, "/api/v1/me/tasks?view=overdue");
        assertThat(titles(archived)).isEmpty();
        assertThat(archived.at("/counts/overdue").asInt()).isZero();
        assertThat(archived.at("/counts/assignedByMe").asInt()).isZero();
        assertThat(read(admin, tasksUrl(layla)).get("open")).hasSize(2);
        assertThat(overdueNoticeOf(overdueId)).isNull();

        assertThat(write(admin, "POST", "/api/v1/projects/" + projectId + "/restore", "{}")
                .getResponse().getStatus()).isEqualTo(200);
        dueScanner.scanAt(now);

        assertThat(titles(read(admin, "/api/v1/me/tasks?view=overdue"))).containsExactly("Overdue one");
        assertThat(overdueNoticeOf(overdueId)).isNotNull();
    }

    @Test
    @DisplayName("a title is one line, since it becomes an email subject")
    void aTitleIsOneLine() throws Exception {
        MvcResult refused = write(admin, "POST", tasksUrl(layla), """
                {"title":"Call back\\r\\nBcc: someone@example.com","kind":"todo","assigneeUserId":"%s",
                 "dueAt":"2026-11-02T09:00:00Z"}""".formatted(researcherId));

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(refused).at("/fieldErrors/title").asText()).isEqualTo("Keep it to one line");
    }

    @Test
    @DisplayName("a change that changes nothing is not audited")
    void aChangeThatChangesNothingIsNotAudited() throws Exception {
        String taskId = body(created(admin, layla, "Request CV", adminId, "2026-11-02T09:00:00Z")).get("id").asText();

        write(admin, "PATCH", "/api/v1/tasks/" + taskId, "{}");
        write(admin, "PATCH", "/api/v1/tasks/" + taskId, """
                {"title":"Request CV"}""");
        assertThat(updatesAuditedOf(taskId)).isZero();

        write(admin, "PATCH", "/api/v1/tasks/" + taskId, """
                {"title":"Request the latest CV"}""");
        assertThat(updatesAuditedOf(taskId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an admin without a seat who stops being one hands their open tasks there to the lead")
    void aDemotedAdminHandsTheirTasksToTheLead() throws Exception {
        String omar = member("omar");
        String omarMember = memberIdOf(admin, "omar@" + domain);
        String omarId = userIdOf(omar);
        assertThat(write(admin, "PATCH", "/api/v1/members/" + omarMember, """
                {"roles":["ADMIN","MEMBER"]}""").getResponse().getStatus()).isEqualTo(200);
        created(admin, layla, "Request CV", omarId, "2026-11-02T09:00:00Z");

        assertThat(write(admin, "PATCH", "/api/v1/members/" + omarMember, """
                {"roles":["MEMBER"]}""").getResponse().getStatus()).isEqualTo(200);

        JsonNode open = read(admin, tasksUrl(layla)).get("open");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).at("/assignee/userId").asText()).isEqualTo(adminId);
        assertThat(timelineKinds()).contains("TASK_REASSIGNED");
    }

    @Test
    @DisplayName("a teammate leaving the position hands their open tasks to its lead, who is told")
    void leavingHandsTasksToTheLead() throws Exception {
        String taskId = body(created(admin, layla, "Request CV", researcherId, "2026-11-02T09:00:00Z"))
                .get("id").asText();

        mvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, "sara@" + domain))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(db.queryForObject("select assignee_user_id::text from app_lm_task where id = ?::uuid",
                String.class, taskId)).isEqualTo(adminId);
        assertThat(timelineKinds()).contains("TASK_REASSIGNED");
        JsonNode bell = read(admin, "/api/v1/me/notifications?filter=unread");
        assertThat(bell.get("items")).anySatisfy(row -> {
            assertThat(row.get("kind").asText()).isEqualTo("tasks_moved");
            assertThat(row.get("count").asInt()).isEqualTo(1);
            assertThat(row.at("/actor/fullName").asText()).isEqualTo("sara");
        });
    }

    @Test
    @DisplayName("the workspace's presets are seeded, named once, retired rather than deleted and ordered by an admin")
    void presetsAreTheWorkspaces() throws Exception {
        JsonNode catalog = read(researcher, "/api/v1/task-presets");
        assertThat(catalog).hasSize(8);
        assertThat(catalog.get(0).get("label").asText()).isEqualTo("Call to qualify interest");
        assertThat(catalog.get(0).get("kind").asText()).isEqualTo("call");
        assertThat(catalog.get(0).get("dueOffsetDays").asInt()).isEqualTo(1);

        assertThat(write(researcher, "POST", "/api/v1/task-presets", """
                {"label":"Check notice period","kind":"todo","dueOffsetDays":2}""").getResponse().getStatus())
                .isEqualTo(403);
        JsonNode added = body(write(admin, "POST", "/api/v1/task-presets", """
                {"label":"Check notice period","kind":"todo","dueOffsetDays":2}"""));
        assertThat(codeOf(write(admin, "POST", "/api/v1/task-presets", """
                {"label":"check NOTICE period","kind":"call"}"""))).isEqualTo("TASK_PRESET_EXISTS");

        JsonNode retired = body(write(admin, "PATCH", "/api/v1/task-presets/" + added.get("id").asText(), """
                {"retired":true}"""));
        assertThat(retired.get("retired").asBoolean()).isTrue();
        assertThat(retired.get("retiredAt").isNull()).isFalse();

        List<String> live = new ArrayList<>();
        read(admin, "/api/v1/task-presets").forEach(preset -> {
            if (!preset.get("retired").asBoolean()) {
                live.add(0, preset.get("id").asText());
            }
        });
        JsonNode reordered = body(write(admin, "PUT", "/api/v1/task-presets/order",
                "{\"presetIds\":" + json.writeValueAsString(live) + "}"));
        assertThat(reordered.get(0).get("label").asText()).isEqualTo("Re-engage later");
        assertThat(reordered.get(8).get("label").asText()).isEqualTo("Check notice period");
    }

    @Test
    @DisplayName("a title the team keeps typing is offered as a preset")
    void typedTitlesAreSuggested() throws Exception {
        for (int typed = 0; typed < 3; typed++) {
            created(admin, layla, "Confirm interview availability", adminId, "2026-11-02T09:00:00Z");
        }
        JsonNode suggestions = read(admin, "/api/v1/task-presets/suggestions");
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.get("label").asText()).isEqualTo("Confirm interview availability");
            assertThat(suggestion.get("count").asInt()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("a reply files one task for whoever sent the sequence, however often it is reported, and a second "
            + "reply while it is open files none")
    void aReplyFilesOneTask() throws Exception {
        UUID messageId = UUID.randomUUID();
        replied(messageId);
        replied(messageId);
        replied(UUID.randomUUID());

        JsonNode open = read(researcher, tasksUrl(layla)).get("open");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("title").asText()).isEqualTo("Reply to Layla Haddad");
        assertThat(open.get(0).get("source").asText()).isEqualTo("rule");
        assertThat(open.get(0).get("rule").asText()).isEqualTo("reply");
        assertThat(open.get(0).at("/assignee/userId").asText()).isEqualTo(researcherId);
        assertThat(open.get(0).get("createdBy").isNull()).isTrue();
    }

    @Test
    @DisplayName("a rule switched off files no task, and the sender is still told of the reply")
    void aSwitchedOffRuleFilesNothing() throws Exception {
        assertThat(write(researcher, "PUT", "/api/v1/workspace/task-rules", """
                {"reply":false,"bounce":true,"bookedCall":true}""").getResponse().getStatus()).isEqualTo(403);
        JsonNode rules = body(write(admin, "PUT", "/api/v1/workspace/task-rules", """
                {"reply":false,"bounce":true,"bookedCall":true}"""));
        assertThat(rules.get("reply").asBoolean()).isFalse();

        replied(UUID.randomUUID());

        assertThat(read(researcher, tasksUrl(layla)).get("open")).isEmpty();
        assertThat(read(researcher, "/api/v1/me/notifications").get("items")).anySatisfy(row -> {
            assertThat(row.get("kind").asText()).isEqualTo("outreach_replied");
            assertThat(row.get("taskId").isNull()).isTrue();
        });
    }

    private void replied(UUID messageId) {
        UUID personId = db.queryForObject("select person_id from app_lm_project_candidate where id = ?::uuid",
                UUID.class, layla);
        transactions.executeWithoutResult(status -> events.publishEvent(new OutreachReplied(
                UUID.fromString(workspaceId), UUID.fromString(projectId), UUID.fromString(layla), personId,
                UUID.randomUUID(), UUID.fromString(researcherId), messageId)));
    }

    private MvcResult created(String token, String candidateId, String title, String assigneeId, String dueAt)
            throws Exception {
        return write(token, "POST", tasksUrl(candidateId), """
                {"title":"%s","kind":"todo","assigneeUserId":"%s","dueAt":"%s"}"""
                .formatted(title, assigneeId, dueAt));
    }

    private JsonNode read(String token, String url) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<String> timelineKinds() throws Exception {
        List<String> kinds = new ArrayList<>();
        read(admin, candidatesUrl(projectId) + "/" + layla + "/timeline").get("entries")
                .forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }

    private int updatesAuditedOf(String taskId) {
        return db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'TASK_UPDATED' AND metadata ->> 'taskId' = ?""", Integer.class, taskId);
    }

    private Object overdueNoticeOf(String taskId) {
        return db.queryForObject("select overdue_notified_at from app_lm_task where id = ?::uuid", Object.class,
                taskId);
    }

    private String tasksUrl(String candidateId) {
        return candidatesUrl(projectId) + "/" + candidateId + "/tasks";
    }

    private String userIdOf(String token) throws Exception {
        return read(token, "/api/v1/auth/me").get("id").asText();
    }

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("tasks").forEach(task -> titles.add(task.get("title").asText()));
        return titles;
    }
}

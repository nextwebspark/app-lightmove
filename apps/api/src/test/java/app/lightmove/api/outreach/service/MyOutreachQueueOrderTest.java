package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.outreach.constant.ConversationGroup;
import app.lightmove.api.outreach.dto.ConversationRowResponse;
import app.lightmove.api.outreach.dto.MyConversationRowResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyOutreachQueueOrderTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void rankReplyDeliveryProblemAndOverdueActionAheadOfFollowUpsNotYetDue() {
        MyConversationRowResponse followUpNextWeek = row("follow-up next week", ConversationGroup.FOLLOW_UP,
                NOW.plus(Duration.ofDays(7)));
        MyConversationRowResponse preparationToday = row("preparation today", ConversationGroup.PREPARATION,
                NOW.plus(Duration.ofHours(3)));
        MyConversationRowResponse deliveryProblem = row("delivery problem", ConversationGroup.ATTENTION, null);
        MyConversationRowResponse overdueFollowUp = row("overdue follow-up", ConversationGroup.FOLLOW_UP,
                NOW.minus(Duration.ofDays(2)));
        MyConversationRowResponse reply = row("reply", ConversationGroup.NEEDS_REPLY, NOW.plus(Duration.ofDays(1)));

        List<MyConversationRowResponse> rows = new ArrayList<>(
                List.of(followUpNextWeek, preparationToday, deliveryProblem, overdueFollowUp, reply));
        rows.sort(MyOutreachQueueService.mostPressingFirst(NOW));

        assertThat(rows.subList(0, 3)).containsExactlyInAnyOrder(deliveryProblem, overdueFollowUp, reply);
        assertThat(rows.subList(3, 5)).containsExactly(preparationToday, followUpNextWeek);
    }

    private static MyConversationRowResponse row(String name, ConversationGroup group, Instant dueAt) {
        CandidateOpenTaskDto action = dueAt == null ? null
                : new CandidateOpenTaskDto(UUID.randomUUID(), name, "follow_up", null, dueAt, null);
        ConversationRowResponse row = new ConversationRowResponse(UUID.randomUUID(), UUID.randomUUID(), name, null,
                null, null, null, null, null, group, action, null, null, null, null, NOW.minus(Duration.ofDays(1)));
        return new MyConversationRowResponse(UUID.randomUUID(), "Position", row);
    }
}

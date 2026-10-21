package app.lightmove.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.task.constant.TaskRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The outreach queue groups people by these names; a rename here would empty Needs reply and Attention silently. */
class OpenTaskRuleNamesTest {

    @Test
    @DisplayName("the open-task seam spells the reply and bounce rules as task does")
    void theSeamSpellsTheRulesAsTaskDoes() {
        assertThat(TaskRule.REPLY.value()).isEqualTo(CandidateOpenTaskDto.REPLY_RULE);
        assertThat(TaskRule.BOUNCE.value()).isEqualTo(CandidateOpenTaskDto.BOUNCE_RULE);
    }
}

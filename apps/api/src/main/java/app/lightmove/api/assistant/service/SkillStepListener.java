package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.agent.tools.ToolCallListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Each playbook loaded becomes a step and an audit entry — only one that exists, since the model writes
 * the name. Never throws: the library does not guard a listener, and a throw would lose the playbook.
 */
@RequiredArgsConstructor
class SkillStepListener implements ToolCallListener {

    private final TurnRecorder recorder;
    private final List<String> skillNames;
    private final ObjectMapper json;

    @Override
    public Object beforeCall(String toolName, String toolInput) {
        String skill = skillOf(toolInput);
        if (skill == null) {
            return null;
        }
        recorder.usedSkill(skill);
        return recorder.startStep("Following the " + skill.replace('-', ' ') + " playbook");
    }

    @Override
    public void afterCompletion(Object context, String toolName, String toolInput) {
        if (context instanceof Integer step) {
            recorder.finishStep(step, null);
        }
    }

    private String skillOf(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        try {
            JsonNode command = json.readTree(toolInput).path("command");
            return command.isString() && skillNames.contains(command.asString()) ? command.asString() : null;
        } catch (JacksonException malformed) {
            return null;
        }
    }
}

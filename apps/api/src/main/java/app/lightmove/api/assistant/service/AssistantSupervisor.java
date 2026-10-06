package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Chooses who answers an ask. With one specialist available it answers directly — no supervisor round,
 * so a single domain costs what it did before specialists existed; with several, the supervisor's model
 * asks them as tools and answers from what they said.
 */
@Slf4j
@Service
public class AssistantSupervisor {

    private final List<AssistantSpecialist> specialists;
    private final AssistantModelCall model;
    private final ObjectMapper json;
    private final AssistantSettings settings;

    public AssistantSupervisor(List<AssistantSpecialist> specialists, AssistantModelCall model, ObjectMapper json,
                               LightMoveProperties properties) {
        this.specialists = List.copyOf(specialists);
        this.model = model;
        this.json = json;
        this.settings = properties.assistant();
    }

    public String answer(String question, List<AssistantTurn> history, AssistantToolContext context) {
        List<AssistantSpecialist> available = specialists.stream()
                .filter(specialist -> specialist.availableTo(context))
                .toList();
        if (available.isEmpty()) {
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
        available.forEach(specialist -> specialist.prepare(history, context.recorder()));
        return available.size() == 1
                ? answerAlone(available.getFirst(), question, history, context)
                : answerTogether(available, question, history, context);
    }

    private String answerAlone(AssistantSpecialist specialist, String question, List<AssistantTurn> history,
                               AssistantToolContext context) {
        context.recorder().consulted(specialist.domain());
        String answer = unavailableOnFailure(() -> model.askSpecialist(specialist, history, question, context));
        specialist.afterAnswer(context);
        return answer;
    }

    private String answerTogether(List<AssistantSpecialist> available, String question,
                                  List<AssistantTurn> history, AssistantToolContext context) {
        List<SpecialistToolCallback> tools = available.stream()
                .map(specialist -> new SpecialistToolCallback(specialist, model, json, history, question,
                        settings.maxSpecialistCallsPerAsk()))
                .toList();
        return unavailableOnFailure(() -> model.askSupervisor(tools, history, question, context));
    }

    private static String unavailableOnFailure(Supplier<String> modelCall) {
        try {
            return modelCall.get();
        } catch (RuntimeException failed) {
            log.warn("Assistant model call failed", failed);
            throw ApiException.of(ErrorCode.ASSISTANT_UNAVAILABLE);
        }
    }
}

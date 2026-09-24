package app.lightmove.api.assistant.tool;

import app.lightmove.api.position.dto.MandateContextDto;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.ResponsibilityDto;
import app.lightmove.api.position.dto.StrategicPriorityDto;
import app.lightmove.api.position.service.PositionService;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Tells the model which position the mandate is hiring for. */
@Component
@RequiredArgsConstructor
public class MandateTools {

    private static final int MAX_RESPONSIBILITIES = 10;
    private static final int MAX_NARRATIVE = 600;

    private final PositionService positions;

    @Tool(description = """
            Read the position this mandate is hiring for: title, seniority, department, location, \
            responsibilities and why the search exists. Call it before answering anything that \
            depends on the role, such as which sectors or companies suit this position.""")
    public MandateBrief readMandateBrief(ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        int step = recorder.startStep("Reading the position brief");
        MandateBrief brief = summarise(positions.briefOf(context.workspaceId(), context.projectId()));
        recorder.finishStep(step, describe(brief));
        return brief;
    }

    static MandateBrief summarise(PositionResponse brief) {
        PositionDetailsDto details = brief.details();
        MandateContextDto context = brief.context();
        return new MandateBrief(
                details.roleTitle(),
                details.seniority() == null ? null : details.seniority().name(),
                details.department(),
                details.locationCity(),
                details.locationCountry(),
                details.responsibilities().stream()
                        .map(ResponsibilityDto::text)
                        .limit(MAX_RESPONSIBILITIES)
                        .toList(),
                truncate(details.narrative()),
                context.mandateReason() == null ? null : context.mandateReason().name(),
                context.businessDriver(),
                context.strategicPriorities().stream()
                        .filter(StrategicPriorityDto::selected)
                        .map(StrategicPriorityDto::name)
                        .toList());
    }

    /** "Chief Financial Officer · Group Finance · Dubai, United Arab Emirates". */
    static String describe(MandateBrief brief) {
        String location = Stream.of(brief.locationCity(), brief.locationCountry())
                .filter(MandateTools::hasText)
                .collect(Collectors.joining(", "));
        String described = Stream.of(brief.roleTitle(), brief.department(), location)
                .filter(MandateTools::hasText)
                .collect(Collectors.joining(" · "));
        boolean drafted = !brief.responsibilities().isEmpty() || hasText(brief.narrative())
                || hasText(brief.businessDriver());
        return drafted ? described : described + (described.isEmpty() ? "" : " · ") + "no brief written yet";
    }

    private static String truncate(String text) {
        if (!hasText(text)) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_NARRATIVE ? flattened : flattened.substring(0, MAX_NARRATIVE) + "…";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package app.lightmove.api.assistant.tool;

import app.lightmove.api.position.dto.MandateContextDto;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.ResponsibilityDto;
import app.lightmove.api.position.dto.StrategicPriorityDto;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.model.MandateFacts;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/** Tells the model which mandate it is working on: the role and the client. */
@Component
@RequiredArgsConstructor
public class MandateTools {

    private static final int MAX_RESPONSIBILITIES = 10;
    private static final int MAX_NARRATIVE = 600;

    private final ProjectService projects;
    private final PositionService positions;
    private final ApolloCompanyQueryService universe;

    @Tool(description = """
            Read the mandate this conversation is about: the role being hired (title, seniority, \
            department, location, responsibilities, why the search exists) and the client (name, \
            sector, headquarters, headcount when the universe carries it). Call it before answering anything that depends on the role or the \
            client, such as which sectors or companies suit this position.""")
    public MandateBrief readMandateBrief(ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        int step = recorder.startStep("Reading the position brief");

        MandateFacts mandate = projects.mandateOf(context.workspaceId(), context.projectId())
                .orElse(new MandateFacts(null, null, null, null, null, null));
        PositionResponse brief = positions.briefOf(context.workspaceId(), context.projectId());
        MandateBrief summary = summarise(mandate, employeesOf(mandate.clientApolloAccountId()), brief);

        recorder.finishStep(step, describe(summary));
        return summary;
    }

    private Integer employeesOf(String apolloAccountId) {
        if (apolloAccountId == null) {
            return null;
        }
        return universe.byAccountIds(List.of(apolloAccountId)).stream()
                .findFirst()
                .map(CompanyRow::numEmployees)
                .orElse(null);
    }

    static MandateBrief summarise(MandateFacts mandate, Integer clientEmployees, PositionResponse brief) {
        PositionDetailsDto details = brief.details();
        MandateContextDto context = brief.context();
        return new MandateBrief(
                mandate.positionTitle(),
                details.seniority() == null ? null : details.seniority().name(),
                details.department(),
                details.locationCity(),
                details.locationCountry(),
                mandate.clientName(),
                mandate.clientSector(),
                mandate.clientHqCity(),
                mandate.clientHqCountry(),
                clientEmployees,
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

    /** "Chief Financial Officer · client Kalem Company (Banking, Dubai, United Arab Emirates, 1,200 staff)". */
    static String describe(MandateBrief brief) {
        String client = brief.clientName() == null ? null
                : "client " + brief.clientName() + bracketed(brief.clientSector(), brief.clientHqCity(),
                        brief.clientHqCountry(), staffOf(brief.clientEmployees()));
        String described = Stream.of(brief.roleTitle(), client)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" · "));
        boolean drafted = !brief.responsibilities().isEmpty() || hasText(brief.narrative())
                || hasText(brief.businessDriver());
        return drafted ? described : described + (described.isEmpty() ? "" : " · ") + "no brief written yet";
    }

    private static String staffOf(Integer employees) {
        return employees == null ? null : String.format(Locale.ROOT, "%,d staff", employees);
    }

    private static String bracketed(String... parts) {
        String joined = Stream.of(parts).filter(MandateTools::hasText).collect(Collectors.joining(", "));
        return joined.isEmpty() ? "" : " (" + joined + ")";
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

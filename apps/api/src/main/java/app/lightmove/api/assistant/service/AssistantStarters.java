package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.StarterKind;
import app.lightmove.api.assistant.dto.AssistantStarter;
import app.lightmove.api.assistant.dto.AssistantStartersResponse;
import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.workspace.model.FirmFacts;
import app.lightmove.api.workspace.model.WorkspacePersona;
import app.lightmove.api.workspace.service.FirmService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The questions an empty chat offers, drawn from the firm: its sectors, their neighbours, and companies
 * of its size. The persona's sectors win over the company's universe industry, often only a neighbour.
 */
@Service
@RequiredArgsConstructor
public class AssistantStarters {

    private static final String ASSUMED_SECTOR = "retail";
    private static final int MAX_TEXT = 80;
    private static final int OPEN_ENDED_ABOVE = 10_000;
    private static final int MAX_SECTOR_STARTERS = 2;

    private final FirmService firms;
    private final IndustryAdjacency adjacency;

    public AssistantStartersResponse forWorkspace(UUID workspaceId) {
        FirmFacts firm = firms.firmOf(workspaceId);
        WorkspacePersona persona = firm.persona() == null ? WorkspacePersona.empty() : firm.persona();
        List<String> recorded = recordedSectors(persona.sectors(), firm.industry());
        String sector = recorded.isEmpty() ? ASSUMED_SECTOR : recorded.getFirst();
        String place = firstPresent(firm.country(), first(persona.geographies()));
        String where = place == null ? "" : " in " + place;
        String sectorName = Industries.displayNameOf(sector);

        List<AssistantStarter> starters = new ArrayList<>();
        for (String each : recorded.isEmpty() ? List.of(sector) : recorded) {
            starters.add(new AssistantStarter(StarterKind.SECTOR,
                    "Top 10 " + Industries.displayNameOf(each) + " companies" + where));
        }
        List<String> neighbours = neighboursPreferringPersona(sector, persona.sectors());
        if (!neighbours.isEmpty()) {
            starters.add(new AssistantStarter(StarterKind.ADJACENT, "Companies in the sectors next to "
                    + sectorName + where + " whose executives move well into " + sectorName));
            starters.add(new AssistantStarter(StarterKind.ADJACENT, "Top " + Industries.displayNameOf(neighbours.getFirst())
                    + " companies" + where + " with executives who could move into " + sectorName));
        }
        Integer employees = firm.employees();
        String name = clean(firm.name());
        if (!recorded.isEmpty() && employees != null && employees > 0 && name != null) {
            starters.add(new AssistantStarter(StarterKind.SIZE,
                    sectorName + " companies" + where + " with " + sizeBand(employees)
                            + ", similar in size to " + name));
        }
        return new AssistantStartersResponse(recorded.isEmpty(), List.copyOf(starters));
    }

    private static List<String> recordedSectors(List<String> personaSectors, String industry) {
        Map<String, String> distinct = new LinkedHashMap<>();
        (personaSectors == null ? List.<String>of() : personaSectors).stream()
                .map(AssistantStarters::clean)
                .filter(Objects::nonNull)
                .forEach(value -> distinct.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        if (distinct.isEmpty()) {
            String cleanIndustry = clean(industry);
            return cleanIndustry == null ? List.of() : List.of(cleanIndustry);
        }
        return distinct.values().stream().limit(MAX_SECTOR_STARTERS).toList();
    }

    private List<String> neighboursPreferringPersona(String sector, List<String> personaSectors) {
        List<String> neighbours = adjacency.neighboursOf(sector).stream()
                .filter(neighbour -> !neighbour.equalsIgnoreCase(sector))
                .toList();
        Set<String> preferred = personaSectors.stream()
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> ordered = new ArrayList<>(neighbours.stream().filter(preferred::contains).toList());
        neighbours.stream().filter(neighbour -> !preferred.contains(neighbour)).forEach(ordered::add);
        return ordered;
    }

    private static String sizeBand(int employees) {
        if (employees > OPEN_ENDED_ABOVE) {
            return "more than 5,000 staff";
        }
        long lower = Math.max(1, roundedToNiceNumber(employees / 2.0));
        long upper = Math.max(lower + 1, roundedToNiceNumber(employees * 2.0));
        return String.format(Locale.ROOT, "%,d to %,d staff", lower, upper);
    }

    /** The nearest of 1, 2 or 5 times a power of ten, so a band reads like one a person would ask for. */
    private static long roundedToNiceNumber(double value) {
        if (value < 1) {
            return 1;
        }
        double scale = Math.pow(10, Math.floor(Math.log10(value)));
        double best = scale;
        for (double step : new double[] {1, 2, 5, 10}) {
            if (Math.abs(step * scale - value) < Math.abs(best - value)) {
                best = step * scale;
            }
        }
        return Math.round(best);
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String firstPresent(String... values) {
        return Arrays.stream(values).map(AssistantStarters::clean)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Persona text is typed by an admin and becomes prompt text, so it is flattened and cut short. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String flat = value.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_TEXT ? flat : flat.substring(0, MAX_TEXT).strip();
    }
}

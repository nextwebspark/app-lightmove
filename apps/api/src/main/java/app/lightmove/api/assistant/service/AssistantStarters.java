package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.StarterKind;
import app.lightmove.api.assistant.dto.AssistantStarter;
import app.lightmove.api.assistant.dto.AssistantStartersResponse;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.workspace.model.FirmFacts;
import app.lightmove.api.workspace.model.WorkspacePersona;
import app.lightmove.api.workspace.service.FirmService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The questions an empty chat offers, drawn from the firm the workspace is: its sector, the sectors
 * whose executives move well into it, and companies of its size. The sector an admin wrote into the
 * persona wins over the picked company's universe industry, which is often a neighbour of the firm's
 * real business (an online retailer filed under internet); a firm with neither is offered retail's.
 */
@Service
@RequiredArgsConstructor
public class AssistantStarters {

    private static final String ASSUMED_SECTOR = "retail";
    private static final int MAX_TEXT = 80;
    private static final int OPEN_ENDED_ABOVE = 10_000;
    private static final Set<String> LOWER_CASE_WORDS = Set.of("&", "and", "of", "the", "for", "in");

    private final FirmService firms;
    private final IndustryAdjacency adjacency;

    public AssistantStartersResponse forWorkspace(UUID workspaceId) {
        FirmFacts firm = firms.firmOf(workspaceId);
        WorkspacePersona persona = firm.persona() == null ? WorkspacePersona.empty() : firm.persona();
        String recorded = firstPresent(first(persona.sectors()), firm.industry());
        String sector = recorded == null ? ASSUMED_SECTOR : recorded;
        String place = firstPresent(firm.country(), first(persona.geographies()));
        String where = place == null ? "" : " in " + place;
        String sectorName = displayed(sector);

        List<AssistantStarter> starters = new ArrayList<>();
        starters.add(new AssistantStarter(StarterKind.SECTOR, "Top 10 " + sectorName + " companies" + where));
        List<String> neighbours = neighboursPreferringPersona(sector, persona.sectors());
        if (!neighbours.isEmpty()) {
            starters.add(new AssistantStarter(StarterKind.ADJACENT, "Companies in the sectors next to "
                    + sectorName + where + " whose executives move well into " + sectorName));
            starters.add(new AssistantStarter(StarterKind.ADJACENT, "Top " + displayed(neighbours.getFirst())
                    + " companies" + where + " with executives who could move into " + sectorName));
        }
        Integer employees = firm.employees();
        String name = clean(firm.name());
        if (recorded != null && employees != null && employees > 0 && name != null) {
            starters.add(new AssistantStarter(StarterKind.SIZE,
                    sectorName + " companies" + where + " with " + sizeBand(employees)
                            + ", similar in size to " + name));
        }
        return new AssistantStartersResponse(recorded == null, List.copyOf(starters));
    }

    /** The firm's own sectors first, when the list puts them beside its industry; then the list's order. */
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

    /** The universe spells industries in lower case; a sector someone typed keeps its own casing. */
    private static String displayed(String sector) {
        if (!sector.equals(sector.toLowerCase(Locale.ROOT))) {
            return sector;
        }
        String[] words = sector.split(" ");
        for (int index = 0; index < words.length; index++) {
            String word = words[index];
            if (word.isEmpty() || (index > 0 && LOWER_CASE_WORDS.contains(word))) {
                continue;
            }
            words[index] = Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return String.join(" ", words);
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

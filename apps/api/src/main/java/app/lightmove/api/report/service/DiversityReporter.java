package app.lightmove.api.report.service;

import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.report.dto.DiversityDto;
import app.lightmove.api.report.dto.GenderLevelRowDto;
import app.lightmove.api.report.dto.GenderSplitDto;
import app.lightmove.api.report.dto.LevelCountDto;
import app.lightmove.api.report.dto.NationalityRowDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chapter four. Nationality is counted under one spelling per group, by seniority, with the tail
 * folded into "Other" past the cap and the Gulf nationals totalled — the figure a localisation
 * quota is measured against.
 *
 * <p>Gender is counted the same way and only where a researcher recorded it. Nothing here reads a
 * name, so a mandate that has recorded none is reported as unmeasured rather than as all-male.
 */
@Component
class DiversityReporter {

    private final ReportSettings caps;

    DiversityReporter(LightMoveProperties properties) {
        this.caps = properties.report();
    }

    DiversityDto report(ReportSources sources) {
        List<NationalExecutive> nationals = new ArrayList<>();
        int unknown = 0;
        for (ExecutiveRow row : sources.executives()) {
            String group = NationalityCatalog.groupOf(row.executive().nationality());
            if (group == null) {
                unknown++;
            } else {
                nationals.add(new NationalExecutive(row, group));
            }
        }

        Tally<String> byNationality = new Tally<>();
        nationals.forEach(national -> byNationality.add(national.group()));
        List<String> leading = byNationality.top(caps.maxNationalities()).stream()
                .filter(group -> !ReportVocabulary.OTHER.equals(group))
                .toList();
        List<NationalityRowDto> rows = new ArrayList<>();
        for (String group : leading) {
            rows.add(row(group, NationalityCatalog.isGcc(group), nationals, group::equals));
        }
        if (byNationality.outside(leading) > 0) {
            rows.add(row(ReportVocabulary.OTHER, false, nationals, group -> !leading.contains(group)));
        }

        long gccNationals = nationals.stream().filter(national -> NationalityCatalog.isGcc(national.group())).count();
        return new DiversityDto(ReportVocabulary.levelTokens(), rows, unknown, gccNationals,
                genderByLevel(sources.executives()), genderWithoutLevel(sources.executives()),
                genderUnrecorded(sources.executives()));
    }

    /**
     * The gender split of each level, over the rows that carry one. A level nobody recorded answers
     * three zeros rather than being left out, so the chapter can say it is unmeasured.
     */
    private static List<GenderLevelRowDto> genderByLevel(List<ExecutiveRow> executives) {
        Map<Seniority, List<ExecutiveRow>> byLevel = byLevel(executives);
        return Arrays.stream(Seniority.values())
                .map(level -> {
                    List<ExecutiveRow> here = byLevel.getOrDefault(level, List.of());
                    return new GenderLevelRowDto(level.value(), count(here, Gender.FEMALE),
                            count(here, Gender.MALE), count(here, Gender.OTHER));
                })
                .toList();
    }

    /** Everyone with a seniority on file, by level, in one pass. */
    private static Map<Seniority, List<ExecutiveRow>> byLevel(List<ExecutiveRow> executives) {
        return executives.stream()
                .filter(row -> row.seniority() != null)
                .collect(Collectors.groupingBy(ExecutiveRow::seniority));
    }

    /** Recorded genders on rows with no seniority, which no level's split can hold. */
    private static GenderSplitDto genderWithoutLevel(List<ExecutiveRow> executives) {
        List<ExecutiveRow> unplaced = executives.stream().filter(row -> row.seniority() == null).toList();
        return new GenderSplitDto(count(unplaced, Gender.FEMALE), count(unplaced, Gender.MALE),
                count(unplaced, Gender.OTHER));
    }

    private static int count(List<ExecutiveRow> executives, Gender gender) {
        return (int) executives.stream().filter(row -> row.gender() == gender).count();
    }

    /** Everyone nobody recorded a gender for — counted apart from {@code OTHER}, which somebody did. */
    private static int genderUnrecorded(List<ExecutiveRow> executives) {
        return (int) executives.stream().filter(row -> row.gender() == null).count();
    }

    private static NationalityRowDto row(String label, boolean gcc, List<NationalExecutive> nationals,
                                         Predicate<String> belongs) {
        List<ExecutiveRow> members = nationals.stream()
                .filter(national -> belongs.test(national.group()))
                .map(NationalExecutive::row)
                .toList();
        Map<Seniority, List<ExecutiveRow>> membersByLevel = byLevel(members);
        List<LevelCountDto> levels = Arrays.stream(Seniority.values())
                .map(level -> new LevelCountDto(level.value(), membersByLevel.getOrDefault(level, List.of()).size()))
                .toList();
        int classified = levels.stream().mapToInt(LevelCountDto::count).sum();
        return new NationalityRowDto(label, gcc, levels, members.size() - classified, members.size());
    }

    private record NationalExecutive(ExecutiveRow row, String group) {}
}

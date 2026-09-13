package app.lightmove.api.report.service;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.report.dto.DiversityDto;
import app.lightmove.api.report.dto.LevelCountDto;
import app.lightmove.api.report.dto.NationalityRowDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Chapter four. Nationality is counted under one spelling per group, by seniority, with the tail
 * folded into "Other" past the cap and the Gulf nationals totalled — the figure a localisation
 * quota is measured against.
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
            String demonym = NationalityCatalog.demonymOf(row.executive().nationality());
            if (demonym == null) {
                unknown++;
            } else {
                nationals.add(new NationalExecutive(row, demonym));
            }
        }

        Tally<String> byNationality = new Tally<>();
        nationals.forEach(national -> byNationality.add(national.demonym()));
        List<String> leading = byNationality.top(caps.maxNationalities());
        List<NationalityRowDto> rows = new ArrayList<>();
        for (String demonym : leading) {
            rows.add(row(demonym, NationalityCatalog.isGcc(demonym), nationals, demonym::equals));
        }
        if (byNationality.outside(leading) > 0) {
            rows.add(row(MarketShapeReporter.OTHER, false, nationals, demonym -> !leading.contains(demonym)));
        }

        long gccNationals = nationals.stream().filter(national -> NationalityCatalog.isGcc(national.demonym())).count();
        return new DiversityDto(MarketShapeReporter.levelTokens(), rows, unknown, gccNationals);
    }

    private static NationalityRowDto row(String label, boolean gcc, List<NationalExecutive> nationals,
                                         Predicate<String> belongs) {
        List<ExecutiveRow> members = nationals.stream()
                .filter(national -> belongs.test(national.demonym()))
                .map(NationalExecutive::row)
                .toList();
        List<LevelCountDto> byLevel = Arrays.stream(Seniority.values())
                .map(level -> new LevelCountDto(level.value(),
                        (int) members.stream().filter(row -> row.seniority() == level).count()))
                .toList();
        int unclassified = (int) members.stream().filter(row -> row.seniority() == null).count();
        return new NationalityRowDto(label, gcc, byLevel, unclassified, members.size());
    }

    private record NationalExecutive(ExecutiveRow row, String demonym) {}
}

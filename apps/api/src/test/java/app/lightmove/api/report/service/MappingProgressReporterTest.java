package app.lightmove.api.report.service;

import static app.lightmove.api.report.service.ReportFixtures.company;
import static app.lightmove.api.report.service.ReportFixtures.executive;
import static app.lightmove.api.report.service.ReportFixtures.sources;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.report.dto.MappingProgressDto;
import app.lightmove.api.report.dto.WeeklyCountDto;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chapter one over a mandate three weeks old: who was filed when, and when each company was first reached. */
class MappingProgressReporterTest {

    private static final ReportCalendar THREE_WEEKS =
            new ReportCalendar(LocalDate.parse("2026-07-05"), LocalDate.parse("2026-07-25"), LocalDate.parse("2026-08-16"));

    @Test
    @DisplayName("executives fall in the week and day they were filed, and a company counts from its first one")
    void bucketsAcrossWeeks() {
        TriageCompanyResponse almarai = company("Almarai", "food & beverages");
        TriageCompanyResponse nadec = company("NADEC", "food & beverages");
        TriageCompanyResponse untouched = company("Savola", "food & beverages");

        MappingProgressDto progress = new MappingProgressReporter().report(sources(
                List.of(almarai, nadec, untouched),
                List.of(executive("A One", almarai, "C-Suite", null, null, at("2026-07-06")),
                        executive("A Two", almarai, "N-1", null, null, at("2026-07-14")),
                        executive("N One", nadec, "C-Suite", null, null, at("2026-07-15")),
                        executive("N Two", nadec, "N-1", null, null, at("2026-07-21")),
                        executive("Unmapped", null, "N-1", null, null, at("2026-07-22")))), THREE_WEEKS);

        assertThat(progress.targetCompanies()).isEqualTo(3);
        assertThat(progress.weekly()).containsExactly(
                new WeeklyCountDto(LocalDate.parse("2026-07-11"), 1),
                new WeeklyCountDto(LocalDate.parse("2026-07-18"), 2),
                new WeeklyCountDto(LocalDate.parse("2026-07-25"), 2));
        assertThat(progress.companiesCumulative()).containsExactly(1, 2, 2);
        assertThat(progress.daily()).hasSize(21);
        assertThat(List.of(progress.daily().get(1), progress.daily().get(9), progress.daily().get(10),
                progress.daily().get(16), progress.daily().get(17))).containsOnly(1);
        assertThat(progress.daily().stream().mapToInt(Integer::intValue).sum()).isEqualTo(5);
        assertThat(progress.daysSinceLastCompany()).isEqualTo(10);
    }

    @Test
    @DisplayName("a mandate nobody has mapped yet has flat curves and no last company to count from")
    void nothingMapped() {
        MappingProgressDto progress = new MappingProgressReporter()
                .report(sources(List.of(company("Almarai", null)), List.of()), THREE_WEEKS);

        assertThat(progress.companiesCumulative()).containsExactly(0, 0, 0);
        assertThat(progress.daysSinceLastCompany()).isNull();
    }

    private static Instant at(String date) {
        return Instant.parse(date + "T09:00:00Z");
    }
}

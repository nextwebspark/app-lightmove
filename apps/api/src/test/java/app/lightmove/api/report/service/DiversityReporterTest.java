package app.lightmove.api.report.service;

import static app.lightmove.api.report.service.ReportFixtures.capped;
import static app.lightmove.api.report.service.ReportFixtures.executive;
import static app.lightmove.api.report.service.ReportFixtures.sources;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.report.dto.DiversityDto;
import app.lightmove.api.report.dto.NationalityRowDto;
import app.lightmove.api.report.model.ExecutiveRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chapter four past its cap: two groups named, the rest folded. */
class DiversityReporterTest {

    private static final ReportSettings TWO_GROUPS = new ReportSettings(2000, 5000, 6, 8, 2, 3, 12);

    @Test
    @DisplayName("groups past the cap fold into one Other row that keeps their levels, and Gulf nationals are still totalled")
    void tailFoldsIntoOther() {
        DiversityDto diversity = new DiversityReporter(capped(TWO_GROUPS)).report(sources(List.of(), List.of(
                national("Saudi", "C-Suite"), national("Saudi", "N-1"), national("KSA", null),
                national("Egyptian", "C-Suite"), national("Lebanese", "C-Suite"),
                national("Emirati", "N-1"),
                national("Indian", "C-Suite"),
                national(null, "C-Suite"))));

        assertThat(diversity.nationalities()).extracting(NationalityRowDto::nationality)
                .containsExactly("Saudi", "Arab expat, non-GCC", "Other");
        NationalityRowDto saudi = diversity.nationalities().get(0);
        assertThat(saudi.total()).isEqualTo(3);
        assertThat(saudi.unclassified()).isEqualTo(1);
        NationalityRowDto other = diversity.nationalities().get(2);
        assertThat(other.total()).isEqualTo(2);
        assertThat(other.gcc()).isFalse();
        assertThat(diversity.gccNationals()).isEqualTo(4);
        assertThat(diversity.unknownNationality()).isEqualTo(1);
    }

    @Test
    @DisplayName("a nationality somebody typed as Other joins the folded row instead of drawing a second one")
    void typedOtherIsNotASecondRow() {
        DiversityDto diversity = new DiversityReporter(capped(TWO_GROUPS)).report(sources(List.of(), List.of(
                national("other", "C-Suite"), national("other", "C-Suite"), national("other", "N-1"),
                national("Saudi", "C-Suite"), national("Saudi", "N-1"),
                national("Indian", "C-Suite"))));

        assertThat(diversity.nationalities()).extracting(NationalityRowDto::nationality)
                .containsExactly("Saudi", "Other");
        assertThat(diversity.nationalities().get(1).total()).isEqualTo(4);
    }

    private static ExecutiveRow national(String nationality, String seniority) {
        return executive("Someone", null, seniority, null, nationality, Instant.EPOCH);
    }
}

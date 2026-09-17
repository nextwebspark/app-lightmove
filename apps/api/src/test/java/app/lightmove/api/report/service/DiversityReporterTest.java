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

/** Chapter four past its cap: the nine groups always named, and the spellings nobody could place folded. */
class DiversityReporterTest {

    @Test
    @DisplayName("a spelling the catalog cannot place takes a row only while the cap has room; the rest fold into Other")
    void unplacedSpellingsFoldPastTheCap() {
        DiversityDto diversity = reportCappedAt(5,
                national("Saudi", "C-Suite"), national("Saudi", "N-1"), national("KSA", null),
                national("Egyptian", "C-Suite"), national("Lebanese", "C-Suite"),
                national("Emirati", "N-1"),
                national("Indian", "C-Suite"),
                national("Turkish", "C-Suite"), national("Turkish", "N-1"),
                national("Chinese", "C-Suite"),
                national("Nigerian", "C-Suite"),
                national(null, "C-Suite"));

        assertThat(diversity.nationalities()).extracting(NationalityRowDto::nationality)
                .containsExactly("Saudi", "Arab expat, non-GCC", "Turkish", "Emirati", "South Asian", "Other");
        NationalityRowDto saudi = diversity.nationalities().get(0);
        assertThat(saudi.total()).isEqualTo(3);
        assertThat(saudi.unclassified()).isEqualTo(1);
        NationalityRowDto other = diversity.nationalities().get(5);
        assertThat(other.total()).isEqualTo(2);
        assertThat(other.gcc()).isFalse();
        assertThat(diversity.gccNationals()).isEqualTo(4);
        assertThat(diversity.unknownNationality()).isEqualTo(1);
    }

    @Test
    @DisplayName("a small Gulf group is never the one folded, however many larger spellings compete for the cap")
    void theNineAreNeverFolded() {
        DiversityDto diversity = reportCappedAt(2,
                national("Turkish", "C-Suite"), national("Turkish", "C-Suite"), national("Turkish", "N-1"),
                national("Chinese", "C-Suite"), national("Chinese", "N-1"),
                national("Saudi", "C-Suite"),
                national("Bahraini", "N-1"),
                national("British", "C-Suite"));

        assertThat(diversity.nationalities()).extracting(NationalityRowDto::nationality)
                .containsExactly("Saudi", "Bahraini", "Western expat", "Other");
        assertThat(diversity.nationalities().get(3).total()).isEqualTo(5);
        assertThat(diversity.gccNationals()).isEqualTo(2);
    }

    @Test
    @DisplayName("a nationality somebody typed as Other joins the folded row instead of drawing a second one")
    void typedOtherIsNotASecondRow() {
        DiversityDto diversity = reportCappedAt(5,
                national("other", "C-Suite"), national("other", "C-Suite"), national("other", "N-1"),
                national("Saudi", "C-Suite"), national("Saudi", "N-1"),
                national("Indian", "C-Suite"));

        assertThat(diversity.nationalities()).extracting(NationalityRowDto::nationality)
                .containsExactly("Saudi", "South Asian", "Other");
        assertThat(diversity.nationalities().get(2).total()).isEqualTo(3);
    }

    private static DiversityDto reportCappedAt(int maxNationalities, ExecutiveRow... executives) {
        ReportSettings caps = new ReportSettings(2000, 5000, 6, 8, maxNationalities, 3, 12);
        return new DiversityReporter(capped(caps)).report(sources(List.of(), List.of(executives)));
    }

    private static ExecutiveRow national(String nationality, String seniority) {
        return executive("Someone", null, seniority, null, nationality, Instant.EPOCH);
    }
}

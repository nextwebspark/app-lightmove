package app.lightmove.api.report.service;

import static app.lightmove.api.report.service.ReportFixtures.capped;
import static app.lightmove.api.report.service.ReportFixtures.company;
import static app.lightmove.api.report.service.ReportFixtures.executive;
import static app.lightmove.api.report.service.ReportFixtures.sources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.geocoding.model.GeocodingResult;
import app.lightmove.api.geocoding.service.GeocodingService;
import app.lightmove.api.report.dto.BreakdownDto;
import app.lightmove.api.report.dto.MarketCellDto;
import app.lightmove.api.report.dto.MarketShapeDto;
import app.lightmove.api.report.dto.MarketSliceDto;
import app.lightmove.api.report.dto.TalentHubDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chapter two past its caps: two sectors, two hubs, one employer per hub and one executive per slice. */
class MarketShapeReporterTest {

    private static final ReportSettings TIGHT = new ReportSettings(2000, 5000, 2, 2, 9, 1, 1);

    private final TriageCompanyResponse retailer = company("Retail Co", "retail");
    private final TriageCompanyResponse secondRetailer = company("Second Retail Co", "retail");
    private final TriageCompanyResponse maker = company("Fmcg Co", "consumer goods");
    private final TriageCompanyResponse farm = company("Farm Co", "farming");
    private final TriageCompanyResponse mystery = company("Mystery Co", null);
    private final TriageCompanyResponse secondMystery = company("Mystery Two", null);

    private MarketShapeDto market;

    @BeforeEach
    void report() {
        GeocodingService geocoding = mock(GeocodingService.class);
        when(geocoding.resolve(any())).thenReturn(new GeocodingResult(Map.of(), 0));
        market = new MarketShapeReporter(capped(TIGHT), geocoding).report(sources(
                List.of(retailer, secondRetailer, maker, farm, mystery, secondMystery),
                List.of(person("R One", retailer, "C-Suite", "United Arab Emirates"),
                        person("R Two", secondRetailer, "C-Suite", "United Arab Emirates"),
                        person("R Three", retailer, "N-1", "United Arab Emirates"),
                        person("F One", maker, "C-Suite", "Saudi Arabia"),
                        person("F Two", maker, "C-Suite", "Saudi Arabia"),
                        person("G One", farm, "C-Suite", "Kuwait"),
                        person("M One", mystery, "C-Suite", "Oman"))));
    }

    @Test
    @DisplayName("sectors past the cap share one Other column, and their executives are counted in it")
    void sectorTailFoldsIntoOther() {
        assertThat(market.sectors()).containsExactly("retail", "consumer goods", "Other");
        assertThat(market.cells()).contains(
                new MarketCellDto("retail", "C-Suite", 2),
                new MarketCellDto("retail", "N-1", 1),
                new MarketCellDto("consumer goods", "C-Suite", 2),
                new MarketCellDto("Other", "C-Suite", 1));
        assertThat(market.withoutSector()).isEqualTo(1);
    }

    @Test
    @DisplayName("a slice lists a capped number of its executives while its cell still counts them all")
    void sliceRosterIsCapped() {
        MarketSliceDto retailChiefs = market.slices().stream()
                .filter(slice -> slice.sector().equals("retail") && slice.level().equals("C-Suite"))
                .findFirst().orElseThrow();

        assertThat(retailChiefs.executives()).hasSize(1);
        assertThat(retailChiefs.companies()).containsExactly("Retail Co", "Second Retail Co");
    }

    @Test
    @DisplayName("hubs past the cap are totalled as elsewhere, and a hub names a capped number of employers")
    void hubTailIsCountedElsewhere() {
        assertThat(market.hubs()).extracting(TalentHubDto::country)
                .containsExactly("United Arab Emirates", "Saudi Arabia");
        assertThat(market.hubs().get(0).count()).isEqualTo(3);
        assertThat(market.hubs().get(0).employers()).containsExactly("Retail Co");
        assertThat(market.elsewhere()).isEqualTo(2);
        assertThat(market.unlocated()).isZero();
    }

    @Test
    @DisplayName("companies with no industry and the sectors past the cap are one Other row, never two")
    void companiesBySectorHasOneOther() {
        assertThat(market.companiesBySector()).containsExactly(
                new BreakdownDto("retail", 2),
                new BreakdownDto("Other", 4));
    }

    private static ExecutiveRow person(String name, TriageCompanyResponse company, String seniority,
                                       String country) {
        return executive(name, company, seniority, country, null, Instant.EPOCH);
    }
}

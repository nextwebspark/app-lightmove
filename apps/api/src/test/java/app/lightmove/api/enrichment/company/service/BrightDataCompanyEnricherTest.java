package app.lightmove.api.enrichment.company.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.enrichment.company.service.BrightDataCompanyEnricher.BrightDataCompany;
import app.lightmove.api.enrichment.company.service.BrightDataCompanyEnricher.BrightDataCompanyResult;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The company dataset record → {@link CapturedCompanyDetails} translation, against a fixture shaped
 * on the live probe: snake_case keys, ISO country codes, and plenty of fields this feature ignores.
 */
class BrightDataCompanyEnricherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a company record maps into the details a researcher would have typed")
    void aCompanyRecordMaps() {
        CapturedCompanyDetails details = BrightDataCompanyEnricher.toDetails(fixtureCompany()).orElseThrow();

        assertThat(details.companyName()).isEqualTo("SampleCo");
        assertThat(details.industry()).isEqualTo("Software Development");
        assertThat(details.companyCity()).isEqualTo("Dublin");
        // The dataset speaks ISO-2; the Country column speaks names, as the Apollo rows do.
        assertThat(details.companyCountry()).isEqualTo("Ireland");
        assertThat(details.numEmployees()).isEqualTo(841);
        assertThat(details.website()).isEqualTo("https://www.sampleco.example/");
        assertThat(details.companyLinkedinUrl()).isEqualTo("https://www.linkedin.com/company/sampleco");
        assertThat(details.foundedYear()).isEqualTo(1993);
        assertThat(details.shortDescription()).startsWith("SampleCo is a leading provider");
        assertThat(details.logoUrl()).isEqualTo("https://media.example.com/sampleco-logo.png");
        assertThat(details.annualRevenue()).isNull();
    }

    @Test
    @DisplayName("a record without even a name is no answer at all")
    void aNamelessRecordIsNoAnswer() {
        BrightDataCompany nameless =
                new BrightDataCompany(null, "About text", null, null, null, null, null, null, null, null);

        assertThat(BrightDataCompanyEnricher.toDetails(nameless)).isEmpty();
    }

    private static BrightDataCompany fixtureCompany() {
        InputStream recorded = BrightDataCompanyEnricherTest.class
                .getResourceAsStream("/brightdata/linkedin-company.json");
        return JSON.readValue(recorded, BrightDataCompanyResult.class).hits().getFirst();
    }
}

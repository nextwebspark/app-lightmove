package app.lightmove.api.enrichment.company.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The company dataset record → {@link VendorCompanyRecord} translation, against a fixture shaped on
 * the live probe: snake_case keys, ISO country codes, and plenty of fields this feature ignores.
 */
class BrightDataCompanyEnricherTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a company record keeps the vendor's own words, payload included")
    void aCompanyRecordKeepsWhatTheVendorSaid() {
        VendorCompanyRecord record = fixtureRecord();

        assertThat(record.companyName()).isEqualTo("SampleCo");
        // The vendor's own V2 leaf, unflattened: the cache stores this, and the triage row stores
        // the universe label it resolves to.
        assertThat(record.industry()).isEqualTo("Software Development");
        assertThat(record.companyCity()).isEqualTo("Dublin");
        // The dataset speaks ISO-2; the Country column speaks names, as the Apollo rows do.
        assertThat(record.companyCountry()).isEqualTo("Ireland");
        assertThat(record.employeesInLinkedin()).isEqualTo(841);
        assertThat(record.foundedYear()).isEqualTo(1993);
        // One comma-separated line on the page, lower-cased into what the market-segment filter matches.
        assertThat(record.keywords()).containsExactly("insurance software", "insurance platform");
        assertThat(record.raw()).contains("\"company_id\":\"10801\"");
    }

    @Test
    @DisplayName("the details a researcher would have typed come from the same record")
    void theRecordBecomesCapturedDetails() {
        CapturedCompanyDetails details = fixtureRecord().asCapturedDetails().orElseThrow();

        // The dataset answers in LinkedIn's V2 vocabulary and the universe publishes V1's, so the
        // row files "Software Development" under the label the Strategy filter can ask for.
        assertThat(details.industry()).isEqualTo("computer software");
        // employeesInLinkedin lands in numEmployees, which on a triaged row is the column Apollo's
        // headcount estimate also fills. Two measurements, one column — asserted here so the mapping
        // is never quietly changed; the cache keeps them apart under its own name.
        assertThat(details.numEmployees()).isEqualTo(841);
        assertThat(details.website()).isEqualTo("https://www.sampleco.example/");
        assertThat(details.companyLinkedinUrl()).isEqualTo("https://www.linkedin.com/company/sampleco");
        assertThat(details.shortDescription()).startsWith("SampleCo is a leading provider");
        assertThat(details.logoUrl()).isEqualTo("https://media.example.com/sampleco-logo.png");
        assertThat(details.annualRevenue()).isNull();
    }

    @Test
    @DisplayName("a record without even a name is no answer at all")
    void aNamelessRecordIsNoAnswer() {
        JsonNode nameless = JSON.readTree("""
                {"about":"About text","industries":"Software Development"}""");

        assertThat(BrightDataCompanyEnricher.toRecord("nameless", nameless, JSON)).isEmpty();
    }

    private static VendorCompanyRecord fixtureRecord() {
        InputStream recorded = BrightDataCompanyEnricherTest.class
                .getResourceAsStream("/brightdata/linkedin-company.json");
        JsonNode hit = JSON.readTree(recorded).get("hits").get(0);
        return BrightDataCompanyEnricher.toRecord("sampleco", hit, JSON).orElseThrow();
    }
}

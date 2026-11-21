package app.lightmove.api.enrichment.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.ResearchedLanguage;
import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.ResearchedProfileItem;
import app.lightmove.api.candidate.model.ResearchedProfileLink;
import app.lightmove.api.candidate.model.VendorProfileRecord;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Every research vendor's own record read into one typed shape, and kept whole beside it. */
class ResearchedProfileDetailsReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("a people dataset record keeps what the profile shape drops: reach, awards, memberships, posts")
    void readsThePeopleDatasetsExtras() throws Exception {
        VendorProfileRecord record = VendorProfileRecords.of("brightdata", fixture(), JSON);
        ResearchedProfileDetails details = record.details();

        assertThat(record.vendorSeenAt()).isEqualTo(Instant.parse("2026-11-18T18:34:33.702Z"));
        assertThat(record.record()).contains("people_also_viewed");
        assertThat(details.followers()).isEqualTo(12345L);
        assertThat(details.connections()).isEqualTo(500L);
        assertThat(details.influencer()).isFalse();
        assertThat(details.recommendationsCount()).isEqualTo(3);
        assertThat(details.links()).extracting(ResearchedProfileLink::url).containsExactly("https://sample.example");
        assertThat(details.honorsAndAwards()).singleElement().satisfies(award -> {
            assertThat(award.title()).isEqualTo("Retail Leader of the Year");
            assertThat(award.subtitle()).isEqualTo("Retail Awards");
            assertThat(award.period()).isEqualTo("2024");
        });
        assertThat(details.organizations()).extracting(ResearchedProfileItem::subtitle).containsExactly("Retail Council");
        assertThat(details.courses()).extracting(ResearchedProfileItem::title).containsExactly("Strategy Execution");
        assertThat(details.patents()).isEmpty();
        assertThat(details.certifications()).extracting(ResearchedProfileItem::title).containsExactly("Chartered Director");
        assertThat(details.volunteering()).extracting(ResearchedProfileItem::title).containsExactly("Mentor");
        assertThat(details.posts()).singleElement().satisfies(post ->
                assertThat(post.url()).isEqualTo("https://www.linkedin.com/pulse/sample"));
        assertThat(details.languages()).containsExactly(
                new ResearchedLanguage("English", "Native or bilingual proficiency"), new ResearchedLanguage("Arabic", null));
        assertThat(details.alsoViewedSlugs()).containsExactly("another-person");
        assertThat(details.similarProfileSlugs()).containsExactly("third-person");
    }

    @Test
    @DisplayName("what the person wrote about a post is kept, its markup gone, a grouped post's too")
    void readsRoleDescriptions() throws Exception {
        ResearchedProfileDetails details = VendorProfileRecords.of("brightdata", fixture(), JSON).details();

        assertThat(details.roles()).hasSize(2);
        assertThat(details.roles().getFirst()).satisfies(role -> {
            assertThat(role.company()).isEqualTo("RetailCo");
            assertThat(role.title()).isEqualTo("Chief Executive Officer");
            assertThat(role.period()).isEqualTo("Feb 2025 – Present");
            assertThat(role.description()).isEqualTo("Leads the GCC business.");
        });
        assertThat(details.roles().get(1)).satisfies(role -> {
            assertThat(role.company()).isEqualTo("Amazing Corp");
            assertThat(role.title()).isEqualTo("General Manager");
            assertThat(role.location()).isEqualTo("Luxembourg");
        });
    }

    @Test
    @DisplayName("a profile index document reads its flat employer facts, and its read time from epoch milliseconds")
    void readsABusinessSearchDocument() {
        VendorProfileRecord record = VendorProfileRecords.of("brightdata-business", """
                {"linkedin_person_id":"sample-person","followers":"2.5K","connections":500,"influencer":true,
                 "current_company_name":"RetailCo","current_company_industry":"Retail",
                 "current_company_specialties":"Grocery, Fashion","current_company_headquarters":"Dubai",
                 "similar_profile_ids":["another-person"],"scraped_at":1794963254693}""", JSON);

        assertThat(record.vendorSeenAt()).isEqualTo(Instant.ofEpochMilli(1794963254693L));
        assertThat(record.details().followers()).isEqualTo(25L);
        assertThat(record.details().influencer()).isTrue();
        assertThat(record.details().similarProfileSlugs()).containsExactly("another-person");
        assertThat(record.details().company()).satisfies(company -> {
            assertThat(company.industry()).isEqualTo("Retail");
            assertThat(company.headquarter()).isEqualTo("Dubai");
            assertThat(company.specialties()).containsExactly("Grocery", "Fashion");
        });
    }

    @Test
    @DisplayName("a record that is not a JSON object is not kept")
    void refusesWhatIsNotARecord() {
        assertThat(VendorProfileRecords.of("brightdata", "not json", JSON)).isNull();
        assertThat(VendorProfileRecords.of("brightdata", "[1,2]", JSON)).isNull();
        assertThat(VendorProfileRecords.of("brightdata", null, JSON)).isNull();
    }

    @Test
    @DisplayName("two vendors' readings lay together field by field, the first vendor's ahead")
    void laysTwoReadingsTogether() {
        ResearchedProfileDetails contactOut = ResearchedProfileDetailsReader.read(JSON.readTree("""
                {"headline":"Group CEO","followers":100,"certifications":[{"name":"CFA"}]}"""));
        ResearchedProfileDetails dataset = ResearchedProfileDetailsReader.read(JSON.readTree("""
                {"followers":200,"connections":500,"certifications":[{"title":"Other"}],
                 "honors_and_awards":[{"title":"Award"}]}"""));

        ResearchedProfileDetails merged = contactOut.orElse(dataset);

        assertThat(merged.headline()).isEqualTo("Group CEO");
        assertThat(merged.followers()).isEqualTo(100L);
        assertThat(merged.connections()).isEqualTo(500L);
        assertThat(merged.certifications()).extracting(ResearchedProfileItem::title).containsExactly("CFA");
        assertThat(merged.honorsAndAwards()).extracting(ResearchedProfileItem::title).containsExactly("Award");
    }

    private static String fixture() throws Exception {
        try (InputStream in = ResearchedProfileDetailsReaderTest.class
                .getResourceAsStream("/brightdata/linkedin-profile-extras.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

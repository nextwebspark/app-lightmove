package app.lightmove.api.enrichment.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.common.model.ContactOutProfileDetails;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutEmployerKey;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A People Search answer read into the record shape every sourcing door files from, the provider's own kept beside it. */
class ContactOutPeopleRecordsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ContactOutEmployerKey HARBOUR_KEY = new ContactOutEmployerKey("harbour-group", "Harbour Group");

    @Test
    @DisplayName("a hit is filed under the searched company, its role there first, and credited to ContactOut")
    void readsAnAnswer() throws Exception {
        BrightDataPeopleHits hits = ContactOutPeopleRecords.toHits(fixture(), HARBOUR_KEY, JSON);

        assertThat(hits.totalHits()).isEqualTo(14);
        assertThat(hits.hits()).singleElement().satisfies(person -> {
            assertThat(person.linkedinId()).isEqualTo("sample-cfo-12ab");
            assertThat(person.position()).isEqualTo("Chief Executive Officer, Port Division");
            assertThat(person.countryCode()).isEqualTo("AE");
            assertThat(person.currentCompany().companyId()).isEqualTo("harbour-group");
            assertThat(person.experience().getFirst().title()).isEqualTo("Chief Executive Officer, Port Division");
            assertThat(person.experience().getFirst().startDate()).isEqualTo("Feb 2026");
            assertThat(person.experience().getFirst().endDate()).isEqualTo("Present");
        });

        BrightDataPerson person = hits.hits().getFirst();
        EnrichedProfile profile = BrightDataPersonProfiles.toEnrichedProfile(person, EnrichmentVendor.CONTACTOUT);
        assertThat(profile.vendor()).isEqualTo(EnrichmentVendor.CONTACTOUT);
        assertThat(profile.title()).isEqualTo("Chief Executive Officer, Port Division");
        assertThat(profile.locationCity()).isEqualTo("Dubai");
        assertThat(profile.career()).extracting(entry -> entry.title())
                .containsExactly("Chief Executive Officer, Port Division", "Senior Advisor", "Chief Financial Officer");
        assertThat(profile.education()).singleElement().satisfies(school ->
                assertThat(school.period()).isEqualTo("2005 – 2007"));
    }

    @Test
    @DisplayName("a people-first answer files each person under the employer their own profile names")
    void filesAPeopleFirstHitUnderItsOwnEmployer() throws Exception {
        BrightDataPerson person = ContactOutPeopleRecords.toHits(fixture(), JSON).hits().getFirst();

        assertThat(person.currentCompany().companyId()).isEqualTo("harbour-group");
        assertThat(person.currentCompany().name()).isEqualTo("Harbour Group");
        assertThat(person.experience().getFirst().title()).isEqualTo("Chief Executive Officer, Port Division");
    }

    @Test
    @DisplayName("a profile with no company of its own is filed under the current role its title names, and a blank photo is none")
    void filesAPeopleFirstHitUnderItsCurrentRole() {
        ContactOutSearchAnswer answer = JSON.readValue("""
                {"metadata":{"page":1,"page_size":1,"total_results":1},"profiles":{
                 "https://www.linkedin.com/in/azzi-j":{"full_name":"Azzi J","title":"Finance Manager",
                  "company":{"name":""},"profile_picture_url":"",
                  "experience":[
                   {"title":"Deputy GM","company_name":"SLA MEA","is_current":true,
                    "linkedin_url":"https://www.linkedin.com/company/sla-mea"},
                   {"title":"Finance Manager","company_name":"A.H Trading SAL","is_current":true,
                    "linkedin_url":"https://www.linkedin.com/company/ah-trading"}]}}}""",
                ContactOutSearchAnswer.class);

        BrightDataPerson person = ContactOutPeopleRecords.toHits(answer, JSON).hits().getFirst();

        assertThat(person.currentCompanyName()).isEqualTo("A.H Trading SAL");
        assertThat(person.currentCompany().companyId()).isEqualTo("ah-trading");
        assertThat(person.experience().getFirst().title()).isEqualTo("Finance Manager");
        assertThat(person.usableAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("each profile is kept whole beside its mapping, and read back for what the mapping has no room for")
    void keepsTheProviderRecordAndReadsItsExtras() throws Exception {
        BrightDataPeopleHits hits = ContactOutPeopleRecords.toHits(fixture(), JSON);

        ContactOutProfileDetails details = ContactOutProfileDetailsReader.read(hits.sourceRecordOf(0), JSON)
                .orElseThrow();

        assertThat(hits.sourceRecords()).hasSize(1);
        assertThat(details.headline()).isEqualTo("Group CFO | Board Member");
        assertThat(details.followers()).isEqualTo(1200L);
        assertThat(details.links()).extracting(ContactOutProfileDetails.ProfileLink::url)
                .containsExactly("https://github.com/samplecfo", "https://x.com/samplecfo");
        assertThat(details.certifications()).singleElement().satisfies(certificate -> {
            assertThat(certificate.title()).isEqualTo("Chartered Accountant");
            assertThat(certificate.subtitle()).isEqualTo("ICAEW");
            assertThat(certificate.period()).isEqualTo("2009");
        });
        assertThat(details.projects()).singleElement().satisfies(project ->
                assertThat(project.description()).isEqualTo("Led the listing."));
        assertThat(details.contactAvailability().personalEmail()).isTrue();
        assertThat(details.contactAvailability().workEmail()).isFalse();
        assertThat(details.company().website()).isEqualTo("https://harbour.example");
        assertThat(details.company().foundedYear()).isEqualTo(1998);
        assertThat(details.company().specialties()).containsExactly("Ports", "Logistics", "Shipping");
    }

    @Test
    @DisplayName("a profile carrying none of the extras, or odd types for them, reads as absent rather than failing")
    void readsASparseProfile() {
        ContactOutProfileDetails details = ContactOutProfileDetailsReader.read("""
                {"full_name":"Sparse","followers":"2.5K+","company":"not an object",
                 "certifications":[{"authority":"no title"}, "Plain Certificate"],"contact_availability":null}""",
                JSON).orElseThrow();

        assertThat(details.followers()).isEqualTo(25L);
        assertThat(details.company()).isNull();
        assertThat(details.contactAvailability()).isNull();
        assertThat(details.certifications()).extracting(ContactOutProfileDetails.ProfileItem::title)
                .containsExactly("Plain Certificate");
        assertThat(ContactOutProfileDetailsReader.read("not json", JSON)).isEmpty();
    }

    @Test
    @DisplayName("an answer with nobody in it sends profiles as an empty array, and reads as no hits")
    void readsAnEmptyAnswer() {
        ContactOutSearchAnswer empty = JSON.readValue("""
                {"status_code":200,"metadata":{"page":1,"page_size":25,"total_results":0},"profiles":[]}""",
                ContactOutSearchAnswer.class);

        BrightDataPeopleHits hits = ContactOutPeopleRecords.toHits(empty, HARBOUR_KEY, JSON);

        assertThat(hits.hits()).isEmpty();
        assertThat(hits.totalHits()).isZero();
    }

    private static ContactOutSearchAnswer fixture() throws Exception {
        try (InputStream in = ContactOutPeopleRecordsTest.class.getResourceAsStream("/contactout/people-search.json")) {
            return JSON.readValue(in, ContactOutSearchAnswer.class);
        }
    }
}

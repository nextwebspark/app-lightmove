package app.lightmove.api.enrichment.contact.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.CandidateEmail;
import app.lightmove.api.enrichment.contact.service.ContactOutContactFinder.ContactOutProfile;
import app.lightmove.api.enrichment.contact.service.ContactOutContactFinder.ContactOutResponse;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The ContactOut payload → contacts translation, against fixtures shaped on real answers (anonymised):
 * the email call with its statuses object, and the phone call with the empty-array form of the same
 * field and numbers in every spelling the provider was observed to use.
 */
class ContactOutContactFinderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("an email payload maps work and personal addresses, each with its verification status")
    void anEmailPayloadMaps() {
        List<CandidateEmail> emails = ContactOutContactFinder.toEmails(fixture("people-linkedin-emails"));

        assertThat(emails).extracting(CandidateEmail::address).containsExactly(
                "s.person@retailco.example",
                "sample.person@holdings.example",
                "sample.person@gmail.com",
                "listed.only@elsewhere.example");
        assertThat(emails.get(0).kind()).isEqualTo(CandidateEmail.WORK);
        assertThat(emails.get(0).status()).isNull();
        assertThat(emails.get(1).kind()).isEqualTo(CandidateEmail.WORK);
        assertThat(emails.get(1).isVerified()).isTrue();
        assertThat(emails.get(2).kind()).isEqualTo(CandidateEmail.PERSONAL);
    }

    @Test
    @DisplayName("an address the provider listed without saying which kind is still kept")
    void anUntypedAddressIsKept() {
        List<CandidateEmail> emails = ContactOutContactFinder.toEmails(fixture("people-linkedin-emails"));

        assertThat(emails).last().satisfies(email -> {
            assertThat(email.address()).isEqualTo("listed.only@elsewhere.example");
            assertThat(email.kind()).isNull();
        });
    }

    @Test
    @DisplayName("an empty work_email_status arrives as a JSON array and does not break the mapping")
    void anEmptyStatusMapIsAnArray() {
        ContactOutProfile profile = fixture("people-linkedin-phones");

        assertThat(profile.workEmailStatus()).isInstanceOf(List.class);
        assertThat(ContactOutContactFinder.toEmails(profile)).isEmpty();
    }

    @Test
    @DisplayName("phones are kept exactly as the provider spelled them")
    void phonesAreKeptVerbatim() {
        List<String> phones = ContactOutContactFinder.toPhones(fixture("people-linkedin-phones"));

        assertThat(phones).contains("+12065550100", "651-555-0142", "650 555 0177", "16585550163");
    }

    @Test
    @DisplayName("two spellings of one number are one phone")
    void twoSpellingsAreOnePhone() {
        List<String> phones = ContactOutContactFinder.toPhones(fixture("people-linkedin-phones"));

        assertThat(phones).hasSize(4).doesNotContain("6505550177");
    }

    private static ContactOutProfile fixture(String name) {
        InputStream recorded = ContactOutContactFinderTest.class
                .getResourceAsStream("/contactout/" + name + ".json");
        return JSON.readValue(recorded, ContactOutResponse.class).profile();
    }
}

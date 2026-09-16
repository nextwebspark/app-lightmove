package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a contact lookup is allowed to write: one address promoted onto the row where nobody typed one,
 * a record of having asked even when nothing came back, and contacts that survive everything else the
 * profile does afterwards.
 */
class CandidateContactPromotionTest {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy", null, null,
            "Dubai", "United Arab Emirates", List.of(), List.of(), List.of(), List.of(), null);

    @Test
    @DisplayName("a verified work address is promoted over an unverified one")
    void aVerifiedWorkAddressIsPromoted() {
        Candidate candidate = captured();

        candidate.recordFoundEmails(found(
                new CandidateEmail("unverified@retailco.example", CandidateEmail.WORK, null),
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        assertThat(candidate.getEmail()).isEqualTo("verified@retailco.example");
        assertThat(candidate.getProfile().contacts().emails()).hasSize(2);
    }

    @Test
    @DisplayName("a personal address is promoted only when there is no work one")
    void aPersonalAddressIsTheFallback() {
        Candidate candidate = captured();

        candidate.recordFoundEmails(found(
                new CandidateEmail("someone@gmail.example", CandidateEmail.PERSONAL, null)));

        assertThat(candidate.getEmail()).isEqualTo("someone@gmail.example");
    }

    @Test
    @DisplayName("a found address never overwrites one a researcher typed")
    void aFoundAddressNeverOutranksTheResearcher() {
        Candidate candidate = Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), null,
                CandidateSource.MANUAL, details("as.we.met@them.example"));

        candidate.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        assertThat(candidate.getEmail()).isEqualTo("as.we.met@them.example");
        assertThat(candidate.getProfile().contacts().emails()).hasSize(1);
    }

    @Test
    @DisplayName("a lookup that found nothing still records that it ran")
    void aMissIsRemembered() {
        Candidate candidate = captured();

        candidate.recordFoundEmails(FoundEmails.none("contactout"));

        assertThat(candidate.getEmail()).isNull();
        assertThat(candidate.getProfile().contacts().emails()).isEmpty();
        assertThat(candidate.getProfile().contacts().hasAskedForEmails()).isTrue();
        assertThat(candidate.getProfile().contacts().hasAskedForPhones()).isFalse();
    }

    @Test
    @DisplayName("finding a phone leaves the email channel unasked")
    void theTwoChannelsAreIndependent() {
        Candidate candidate = captured();

        candidate.recordFoundPhones(new FoundPhones("contactout", List.of("+12065550100")));

        assertThat(candidate.getPhone()).isEqualTo("+12065550100");
        assertThat(candidate.getProfile().contacts().hasAskedForPhones()).isTrue();
        assertThat(candidate.getProfile().contacts().hasAskedForEmails()).isFalse();
    }

    @Test
    @DisplayName("a drawer edit carries the found contacts across")
    void aDrawerEditKeepsTheContacts() {
        Candidate candidate = captured();
        candidate.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        candidate.describe(details(null));

        assertThat(candidate.getProfile().contacts().emails()).hasSize(1);
        assertThat(candidate.getProfile().contacts().hasAskedForEmails()).isTrue();
    }

    @Test
    @DisplayName("research landing after a contact lookup keeps the contacts")
    void researchLandingLaterKeepsTheContacts() {
        Candidate candidate = captured();
        candidate.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        candidate.enrich(RESEARCH);

        assertThat(candidate.getProfile().contacts().emails()).hasSize(1);
        assertThat(candidate.getProfile().enrichedAt()).isNotNull();
    }

    private static FoundEmails found(CandidateEmail... emails) {
        return new FoundEmails("contactout", List.of(emails));
    }

    private static Candidate captured() {
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), null,
                CandidateSource.EXTENSION, details(null));
    }

    private static CandidateDetails details(String email) {
        return new CandidateDetails("Sample Person", null, null, CandidateStatus.IDENTIFIED, null,
                email, null, "https://www.linkedin.com/in/sample-profile", null, null, null, null,
                null, null, CandidateCompensation.unknown(),
                new CandidateProfile(null, null, null, null, null, null), null);
    }
}

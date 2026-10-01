package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a person's ledger holds after each kind of write: every door's value recorded against that door, a
 * lookup's answer beside them, a record of having asked even when nothing came back, and the Contact
 * section's own save replacing a channel wholesale.
 */
class CandidateContactLedgerTest {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy", null, null,
            "Dubai", "United Arab Emirates", List.of(), List.of(), List.of(), List.of(), null,
            EnrichmentVendor.BRIGHTDATA);

    @Test
    @DisplayName("a lookup's addresses are listed work first, with the provider's reading")
    void foundAddressesAreListedWorkFirst() {
        Person person = captured();

        person.recordFoundEmails(found(
                new CandidateEmail("someone@gmail.example", CandidateEmail.PERSONAL, null),
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        assertThat(person.emailContacts()).extracting(CandidateContact::getValue)
                .containsExactly("verified@retailco.example", "someone@gmail.example");
        CandidateContact work = person.emailContacts().getFirst();
        assertThat(work.isVerified()).isTrue();
        assertThat(work.getStatus()).isEqualTo("Verified");
        assertThat(work.getSource()).isEqualTo(ContactSource.CONTACTOUT);
        assertThat(person.hasFoundEmails()).isTrue();
    }

    @Test
    @DisplayName("a found address sits beside one a researcher typed; neither replaces the other")
    void aFoundAddressJoinsTheResearchers() {
        Person person = typedBy(CandidateSource.MANUAL, "as.we.met@them.example");

        person.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        assertThat(person.emailContacts()).extracting(CandidateContact::getValue)
                .containsExactly("verified@retailco.example", "as.we.met@them.example");
        assertThat(person.emailContacts()).extracting(CandidateContact::getSource)
                .containsExactly(ContactSource.CONTACTOUT, ContactSource.MANUAL);
    }

    @Test
    @DisplayName("every door records its value against itself")
    void everyDoorRecordsItsValue() {
        assertThat(typedBy(CandidateSource.MANUAL, "typed@them.example").emailContacts().getFirst().getSource())
                .isEqualTo(ContactSource.MANUAL);
        assertThat(typedBy(CandidateSource.CSV, "imported@them.example").emailContacts().getFirst().getSource())
                .isEqualTo(ContactSource.CSV);
        assertThat(typedBy(CandidateSource.EXTENSION, "captured@them.example").emailContacts().getFirst().getSource())
                .isEqualTo(ContactSource.EXTENSION);
    }

    @Test
    @DisplayName("an address the provider confirms becomes the provider's, with its reading")
    void aConfirmedAddressIsClaimedByTheProvider() {
        Person person = typedBy(CandidateSource.MANUAL, "Known@Them.Example");

        person.recordFoundEmails(found(
                new CandidateEmail("known@them.example", CandidateEmail.WORK, "Verified")));

        assertThat(person.emailContacts()).hasSize(1);
        CandidateContact known = person.emailContacts().getFirst();
        assertThat(known.getValue()).isEqualTo("Known@Them.Example");
        assertThat(known.getSource()).isEqualTo(ContactSource.CONTACTOUT);
        assertThat(known.getKind()).isEqualTo(ContactKind.WORK);
        assertThat(known.isVerified()).isTrue();
    }

    @Test
    @DisplayName("a later write that supplies a value adds it and removes nothing")
    void aSuppliedValueAddsAndNeverRemoves() {
        Person person = typedBy(CandidateSource.MANUAL, "first@them.example");

        person.describe(details("second@them.example", null), ContactSource.CSV);

        assertThat(person.emailContacts()).extracting(CandidateContact::getValue)
                .containsExactlyInAnyOrder("first@them.example", "second@them.example");
        assertThat(person.emailContacts()).extracting(CandidateContact::getSource)
                .containsExactlyInAnyOrder(ContactSource.MANUAL, ContactSource.CSV);
    }

    @Test
    @DisplayName("the Contact section's save makes the channel hold exactly what it lists")
    void aContactSaveReplacesTheChannel() {
        Person person = typedBy(CandidateSource.MANUAL, "first@them.example");
        person.recordFoundEmails(found(
                new CandidateEmail("found@retailco.example", CandidateEmail.WORK, null),
                new CandidateEmail("gone@retailco.example", CandidateEmail.PERSONAL, null)));

        person.replaceContacts(ContactChannel.EMAIL, List.of(
                new ContactEntry("found@retailco.example", ContactKind.WORK, false),
                new ContactEntry("new@them.example", ContactKind.PERSONAL, false)), ContactSource.MANUAL);

        assertThat(person.emailContacts()).extracting(CandidateContact::getValue)
                .containsExactly("found@retailco.example", "new@them.example");
        assertThat(person.emailContacts().getFirst().getSource()).isEqualTo(ContactSource.CONTACTOUT);
        assertThat(person.emailContacts().get(1).getSource()).isEqualTo(ContactSource.MANUAL);
        assertThat(person.emailContacts().get(1).getKind()).isEqualTo(ContactKind.PERSONAL);
    }

    @Test
    @DisplayName("a person's verified mark is recorded as theirs, and taken back cleanly")
    void aPersonsVerifiedMarkIsTheirs() {
        Person person = typedBy(CandidateSource.MANUAL, "typed@them.example");

        person.replaceContacts(ContactChannel.EMAIL,
                List.of(new ContactEntry("typed@them.example", ContactKind.WORK, true)), ContactSource.MANUAL);
        CandidateContact marked = person.emailContacts().getFirst();
        assertThat(marked.isVerified()).isTrue();
        assertThat(marked.getStatus()).isEqualTo(CandidateContact.VERIFIED_BY_RESEARCHER);
        assertThat(marked.getSource()).isEqualTo(ContactSource.MANUAL);

        person.replaceContacts(ContactChannel.EMAIL,
                List.of(new ContactEntry("typed@them.example", ContactKind.WORK, false)), ContactSource.MANUAL);
        assertThat(person.emailContacts().getFirst().isVerified()).isFalse();
        assertThat(person.emailContacts().getFirst().getStatus()).isNull();
    }

    @Test
    @DisplayName("the provider's verified mark stays the provider's while it is left on")
    void theProvidersVerifiedMarkStaysTheirs() {
        Person person = captured();
        person.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        person.replaceContacts(ContactChannel.EMAIL,
                List.of(new ContactEntry("verified@retailco.example", ContactKind.PERSONAL, true)),
                ContactSource.MANUAL);

        CandidateContact retagged = person.emailContacts().getFirst();
        assertThat(retagged.getKind()).isEqualTo(ContactKind.PERSONAL);
        assertThat(retagged.getStatus()).isEqualTo("Verified");
        assertThat(retagged.getSource()).isEqualTo(ContactSource.CONTACTOUT);
    }

    @Test
    @DisplayName("respelling a provider's value makes it the person's")
    void respellingAProvidersValueMakesItThePersons() {
        Person person = captured();
        person.recordFoundPhones(new FoundPhones("contactout", List.of("+12065550100")));

        person.replaceContacts(ContactChannel.PHONE,
                List.of(new ContactEntry("+1 (206) 555-0100", null, false)), ContactSource.MANUAL);

        assertThat(person.phoneContacts()).hasSize(1);
        assertThat(person.phoneContacts().getFirst().getValue()).isEqualTo("+1 (206) 555-0100");
        assertThat(person.phoneContacts().getFirst().getSource()).isEqualTo(ContactSource.MANUAL);
    }

    @Test
    @DisplayName("respelling an imported or captured value makes it the person's too")
    void respellingAnyDoorsValueMakesItThePersons() {
        Person imported = typedBy(CandidateSource.CSV, null, "+971500000001");
        Person captured = typedBy(CandidateSource.EXTENSION, "Person@Them.Example");

        imported.replaceContacts(ContactChannel.PHONE,
                List.of(new ContactEntry("+971 50 000 0001", null, false)), ContactSource.MANUAL);
        captured.replaceContacts(ContactChannel.EMAIL,
                List.of(new ContactEntry("person@them.example", null, false)), ContactSource.MANUAL);

        assertThat(imported.phoneContacts().getFirst().getValue()).isEqualTo("+971 50 000 0001");
        assertThat(imported.phoneContacts().getFirst().getSource()).isEqualTo(ContactSource.MANUAL);
        assertThat(captured.emailContacts().getFirst().getValue()).isEqualTo("person@them.example");
        assertThat(captured.emailContacts().getFirst().getSource()).isEqualTo(ContactSource.MANUAL);
    }

    @Test
    @DisplayName("a lookup that found nothing still records that it ran")
    void aMissIsRemembered() {
        Person person = captured();

        person.recordFoundEmails(FoundEmails.none("contactout"));

        assertThat(person.emailContacts()).isEmpty();
        assertThat(person.hasAskedForEmails()).isTrue();
        assertThat(person.hasFoundEmails()).isFalse();
        assertThat(person.hasAskedForPhones()).isFalse();
    }

    @Test
    @DisplayName("a typed address does not make a miss read as a find")
    void aTypedAddressIsNotTheProvidersAnswer() {
        Person person = typedBy(CandidateSource.MANUAL, "typed@them.example");

        person.recordFoundEmails(FoundEmails.none("contactout"));

        assertThat(person.hasAskedForEmails()).isTrue();
        assertThat(person.hasFoundEmails()).isFalse();
        assertThat(person.emailContacts()).hasSize(1);
    }

    @Test
    @DisplayName("finding a phone leaves the email channel unasked, and two spellings are one number")
    void theTwoChannelsAreIndependent() {
        Person person = typedBy(CandidateSource.MANUAL, null, "+1 (206) 555-0100");

        person.recordFoundPhones(new FoundPhones("contactout", List.of("+12065550100", "651-555-0142")));

        assertThat(person.phoneContacts()).extracting(CandidateContact::getValue)
                .containsExactly("+1 (206) 555-0100", "651-555-0142");
        assertThat(person.phoneContacts().getFirst().getSource()).isEqualTo(ContactSource.CONTACTOUT);
        assertThat(person.hasAskedForPhones()).isTrue();
        assertThat(person.hasAskedForEmails()).isFalse();
    }

    @Test
    @DisplayName("a drawer edit of another section carries the found contacts across")
    void aDrawerEditKeepsTheContacts() {
        Person person = captured();
        person.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        person.describe(details(null, null), ContactSource.MANUAL);

        assertThat(person.emailContacts()).hasSize(1);
        assertThat(person.hasAskedForEmails()).isTrue();
    }

    @Test
    @DisplayName("research landing after a contact lookup keeps the contacts")
    void researchLandingLaterKeepsTheContacts() {
        Person person = captured();
        person.recordFoundEmails(found(
                new CandidateEmail("verified@retailco.example", CandidateEmail.WORK, "Verified")));

        person.enrich(RESEARCH);

        assertThat(person.emailContacts()).hasSize(1);
        assertThat(person.getProfile().enrichedAt()).isNotNull();
    }

    private static FoundEmails found(CandidateEmail... emails) {
        return new FoundEmails("contactout", List.of(emails));
    }

    private static Person captured() {
        return Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.EXTENSION, details(null, null));
    }

    private static Person typedBy(CandidateSource door, String email) {
        return typedBy(door, email, null);
    }

    private static Person typedBy(CandidateSource door, String email, String phone) {
        return Person.founded(UUID.randomUUID(), UUID.randomUUID(), door, details(email, phone));
    }

    private static CandidateDetails details(String email, String phone) {
        return new CandidateDetails("Sample Person", null, null, CandidateStatus.IDENTIFIED, null,
                email == null ? List.of() : List.of(ContactEntry.of(email)),
                phone == null ? List.of() : List.of(ContactEntry.of(phone)),
                "https://www.linkedin.com/in/sample-profile", null, null, null, null, null,
                null, null, CandidateCompensation.unknown(),
                new CandidateProfile(null, null, null, null, null), null);
    }
}

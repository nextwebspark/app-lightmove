package app.lightmove.api.candidate.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An executive mapped for a mandate — the other half of a talent map, beside the companies
 * {@code triagecompany} holds.
 *
 * <p><b>The project is the mapping; the company is optional.</b> The note, status and compensation
 * reading are all mandate-specific, so the same person researched for two mandates is two rows, and a
 * person met at a company the universe does not carry has no {@code triageCompanyId}.
 *
 * <p>{@code companyName} is a write-time snapshot that outlives the mapping. V36's
 * {@code ON DELETE SET NULL} is the other half of that pair: removing a company from a mandate must
 * not silently delete the people mapped at it.
 *
 * <p>{@code status}, {@code seniorityLevel}, {@code gender} and {@code source} are stored as enum
 * names, matching their CHECK constraints — {@code N-1} is not a legal identifier.
 */
@Entity
@Table(name = "app_lm_project_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    /** Null when the person's employer is not one of the mandate's triaged companies. */
    @Column(name = "triage_company_id")
    private UUID triageCompanyId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority_level", length = 16)
    private Seniority seniorityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CandidateStatus status;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "location_country")
    private String locationCountry;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "nationality")
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    private Gender gender;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    /**
     * Which of {@link BackgroundField}'s three keys currently hold a value {@link #enrich} proposed
     * that nobody has reviewed since (V68, issue #458). A researcher's own edit that actually changes
     * one of the three removes it here; resubmitting the same value does not, since nothing was
     * actually reviewed and decided.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_inferred_fields", nullable = false)
    private Set<String> aiInferredFields = new HashSet<>();

    @Column(name = "summary")
    private String summary;

    @Column(name = "note")
    private String note;

    @Column(name = "compensation_currency", length = 3)
    private String compensationCurrency;

    @Column(name = "base_salary")
    private Long baseSalary;

    @Column(name = "bonus")
    private Long bonus;

    @Column(name = "allowances")
    private Long allowances;

    @Column(name = "long_term_incentive")
    private Long longTermIncentive;

    @Column(name = "notice_period")
    private String noticePeriod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", nullable = false)
    private CandidateProfile profile = CandidateProfile.empty();

    /**
     * Values for this project's CANDIDATE custom columns, keyed by the column's {@code field_key}.
     * Beside {@link #profile} rather than inside it: that one is a typed record read by field.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", nullable = false)
    private CustomFieldValues customFields = CustomFieldValues.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private CandidateSource source;

    /** Which provider's research filled this in. Null until research lands, and for a row nobody researched. */
    @Enumerated(EnumType.STRING)
    @Column(name = "enriched_by", length = 16)
    private EnrichmentVendor enrichedBy;

    /** The profile page the plugin read this from. Null for every other source. */
    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "added_by", nullable = false, updatable = false)
    private UUID addedBy;

    /**
     * Every email and phone known for this person, whatever door it came through — V54's ledger.
     * A bag the aggregate rewrites from its own methods, the way a position owns its lists; nothing
     * outside this class adds a row. Batched at 500 rather than the usual 50 because the talent map
     * reads every mapped person in one unpaged pass, up to {@code talentmap.max-candidates}, and
     * every one of them is answered with its contacts.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_candidate_contact", joinColumns = @JoinColumn(name = "candidate_id"))
    @BatchSize(size = 500)
    private List<CandidateContact> contacts = new ArrayList<>();

    /**
     * When a contact lookup last asked for each channel. Null: never asked. Set with no rows from the
     * provider: asked, nothing found, and asking again would spend a credit to hear it twice.
     */
    @Column(name = "emails_looked_up_at")
    private Instant emailsLookedUpAt;

    @Column(name = "phones_looked_up_at")
    private Instant phonesLookedUpAt;

    @Column(name = "contacts_looked_up_via", length = 24)
    private String contactsLookedUpVia;

    public static Candidate mapped(UUID projectId, UUID addedBy, UUID triageCompanyId,
                                   CandidateSource source, CandidateDetails details) {
        Candidate candidate = new Candidate();
        candidate.projectId = projectId;
        candidate.addedBy = addedBy;
        candidate.source = source;
        candidate.sourceUrl = details.sourceUrl();
        candidate.triageCompanyId = triageCompanyId;
        candidate.describe(details, ContactSource.ofDoor(source));
        return candidate;
    }

    /**
     * Replaces every editable field. The drawer submits all of them every time, and a field-by-field
     * merge would collapse "omitted" and "cleared" into one request across twenty fields.
     *
     * <p>{@code door} is which way this write came in — the drawer, a spreadsheet, the plugin — and
     * is what the ledger records against an address or number the write supplies.
     */
    public void describe(CandidateDetails details, ContactSource door) {
        this.fullName = details.fullName();
        this.title = details.title();
        this.seniorityLevel = details.seniority();
        this.status = details.status();
        this.companyName = details.employerName();
        this.linkedinUrl = details.linkedinUrl();
        this.locationCountry = details.locationCountry();
        this.locationCity = details.locationCity();
        describeBackground(details);
        this.summary = details.summary();
        this.note = details.note();
        this.compensationCurrency = details.compensation().currency();
        this.baseSalary = details.compensation().baseSalary();
        this.bonus = details.compensation().bonus();
        this.allowances = details.compensation().allowances();
        this.longTermIncentive = details.compensation().longTermIncentive();
        this.noticePeriod = details.compensation().noticePeriod();
        // Not a plain replace: the drawer resubmits only the components it renders (career,
        // languages), and a wholesale overwrite here silently wiped enrichment's fields on the first
        // edit after a capture was enriched.
        this.profile = details.profile().keepingEnrichmentOf(this.profile);
        remember(ContactChannel.EMAIL, details.emails(), door);
        remember(ContactChannel.PHONE, details.phones(), door);
    }

    /**
     * Nationality, gender and years of experience — the three an inference may have proposed. A
     * researcher changing one is what confirms it; resubmitting the same value leaves the flag
     * standing, since nothing was actually reviewed.
     */
    private void describeBackground(CandidateDetails details) {
        if (!Objects.equals(nationality, details.nationality())) {
            aiInferredFields = withoutInferred(BackgroundField.NATIONALITY);
        }
        if (!Objects.equals(gender, details.gender())) {
            aiInferredFields = withoutInferred(BackgroundField.GENDER);
        }
        if (!Objects.equals(yearsExperience, details.yearsExperience())) {
            aiInferredFields = withoutInferred(BackgroundField.YEARS_EXPERIENCE);
        }
        this.nationality = details.nationality();
        this.gender = details.gender();
        this.yearsExperience = details.yearsExperience();
    }

    private Set<String> withoutInferred(BackgroundField field) {
        if (!aiInferredFields.contains(field.key())) {
            return aiInferredFields;
        }
        Set<String> updated = new HashSet<>(aiInferredFields);
        updated.remove(field.key());
        return updated;
    }

    /**
     * What a write supplies joins the ledger; nothing it leaves out is removed. A spreadsheet cell
     * or a capture states one value and says nothing about the others, and the Contact section has
     * its own write for taking a value away. A value already held, from any door, is not written
     * twice — it gains the entry's kind or verified claim if it had none.
     */
    private void remember(ContactChannel channel, List<ContactEntry> entries, ContactSource door) {
        Instant now = Instant.now();
        for (ContactEntry entry : entries) {
            String key = CandidateContact.keyOf(channel, entry.value());
            if (key.isEmpty()) {
                continue;
            }
            CandidateContact held = contactOf(channel, key);
            if (held == null) {
                CandidateContact added = CandidateContact.typed(channel, entry.value(), door);
                added.describedBy(entry, door, now);
                contacts.add(added);
            } else if (held.getKind() == null && !held.isVerified()) {
                held.describedBy(new ContactEntry(held.getValue(), entry.kind(), entry.verified()), door, now);
            }
        }
    }

    /**
     * The Contact section's save: the channel now holds exactly these. A row matched by key takes
     * the entry and keeps its source unless respelled; a new key is the writer's; a key no longer
     * listed is gone, whoever put it there — the person looking at the profile has decided.
     */
    public void replaceContacts(ContactChannel channel, List<ContactEntry> entries, ContactSource door) {
        Instant now = Instant.now();
        Map<String, ContactEntry> wanted = new LinkedHashMap<>();
        for (ContactEntry entry : entries) {
            String key = CandidateContact.keyOf(channel, entry.value());
            if (!key.isEmpty()) {
                wanted.putIfAbsent(key, entry);
            }
        }
        contacts.removeIf(contact -> contact.is(channel) && !wanted.containsKey(contact.getValueKey()));
        wanted.forEach((key, entry) -> {
            CandidateContact held = contactOf(channel, key);
            if (held == null) {
                held = CandidateContact.typed(channel, entry.value(), door);
                contacts.add(held);
            }
            held.describedBy(entry, door, now);
        });
    }

    private CandidateContact contactOf(ContactChannel channel, String key) {
        return contacts.stream()
                .filter(contact -> contact.is(channel) && contact.matches(key))
                .findFirst()
                .orElse(null);
    }

    /**
     * Fills in what research found, and only where nobody has filled anything in — vendor data never
     * outranks a researcher. On a mapped candidate {@code companyName} is the triage snapshot and is
     * never overwritten.
     *
     * <p>Nationality, gender and years of experience follow the same rule, but a value filled from
     * here is also stamped into {@link #aiInferredFields} (issue #458): it is a proposal, not
     * something a researcher recorded, until {@link #describeBackground} sees it changed.
     */
    public void enrich(EnrichedProfile enriched) {
        if (title == null) {
            title = enriched.title();
        }
        if (summary == null) {
            summary = enriched.about();
        }
        if (locationCity == null) {
            locationCity = enriched.locationCity();
        }
        if (locationCountry == null) {
            locationCountry = enriched.locationCountry();
        }
        if (triageCompanyId == null && companyName == null) {
            companyName = enriched.employerName();
        }
        Set<String> inferred = new HashSet<>(aiInferredFields);
        if (nationality == null && enriched.nationality() != null) {
            nationality = enriched.nationality();
            inferred.add(BackgroundField.NATIONALITY.key());
        }
        if (gender == null && enriched.gender() != null) {
            gender = enriched.gender();
            inferred.add(BackgroundField.GENDER.key());
        }
        if (yearsExperience == null && enriched.yearsExperience() != null) {
            yearsExperience = enriched.yearsExperience();
            inferred.add(BackgroundField.YEARS_EXPERIENCE.key());
        }
        this.aiInferredFields = inferred;
        this.enrichedBy = enriched.vendor();
        this.profile = new CandidateProfile(
                profile.career().isEmpty() ? enriched.career() : profile.career(),
                profile.languages().isEmpty() ? enriched.languages() : profile.languages(),
                enriched.education(),
                enriched.skills(),
                Instant.now().toString());
    }

    /**
     * Records what a contact lookup found. The ledger takes every address: the provider's own rows are replaced by this answer, and an
     * address a person had already supplied gains the provider's reading (work or personal,
     * verified) while keeping its own source. The timestamp is stamped even when nothing was found.
     * That is what makes a miss free: the channel reads as asked, and the next press answers from
     * the row instead of buying the same "nothing on record" again.
     */
    public void recordFoundEmails(FoundEmails found) {
        Instant now = Instant.now();
        ContactSource provider = ContactSource.ofProvider(found.source());
        contacts.removeIf(contact -> contact.is(ContactChannel.EMAIL) && contact.isFrom(provider));
        for (CandidateEmail address : found.emails()) {
            CandidateContact held = contactOf(ContactChannel.EMAIL,
                    CandidateContact.keyOf(ContactChannel.EMAIL, address.address()));
            if (held != null) {
                held.claim(provider, address, now);
            } else {
                contacts.add(CandidateContact.foundEmail(address, provider, now));
            }
        }
        emailsLookedUpAt = now;
        contactsLookedUpVia = found.source();
    }

    /** The phone half of {@link #recordFoundEmails}, under the same rules. */
    public void recordFoundPhones(FoundPhones found) {
        Instant now = Instant.now();
        ContactSource provider = ContactSource.ofProvider(found.source());
        contacts.removeIf(contact -> contact.is(ContactChannel.PHONE) && contact.isFrom(provider));
        for (String number : found.phones()) {
            String key = CandidateContact.keyOf(ContactChannel.PHONE, number);
            if (key.isEmpty()) {
                continue;
            }
            CandidateContact held = contactOf(ContactChannel.PHONE, key);
            if (held != null) {
                held.claim(provider, now);
            } else {
                contacts.add(CandidateContact.foundPhone(number, provider, now));
            }
        }
        phonesLookedUpAt = now;
        contactsLookedUpVia = found.source();
    }

    public boolean hasAskedForEmails() {
        return emailsLookedUpAt != null;
    }

    public boolean hasAskedForPhones() {
        return phonesLookedUpAt != null;
    }

    /** Whether a lookup ever answered with an address — the difference between "held" and "nothing on record". */
    public boolean hasFoundEmails() {
        return contactsOf(ContactChannel.EMAIL).stream().anyMatch(contact -> !contact.isSuppliedByAPerson());
    }

    public boolean hasFoundPhones() {
        return contactsOf(ContactChannel.PHONE).stream().anyMatch(contact -> !contact.isSuppliedByAPerson());
    }

    /**
     * The email rows as the drawer lists them: work, then personal, then untagged; within a kind,
     * what a person supplied before what a lookup found, oldest first.
     */
    public List<CandidateContact> emailContacts() {
        return listed(ContactChannel.EMAIL);
    }

    public List<CandidateContact> phoneContacts() {
        return listed(ContactChannel.PHONE);
    }

    private List<CandidateContact> listed(ContactChannel channel) {
        return contactsOf(channel).stream()
                .sorted(Comparator.comparing((CandidateContact contact) -> kindRank(contact.getKind()))
                        .thenComparing(contact -> !contact.isSuppliedByAPerson())
                        .thenComparing(CandidateContact::getFoundAt))
                .toList();
    }

    private List<CandidateContact> contactsOf(ContactChannel channel) {
        return contacts.stream().filter(contact -> contact.is(channel)).toList();
    }

    private static int kindRank(ContactKind kind) {
        if (kind == null) {
            return 2;
        }
        return kind == ContactKind.WORK ? 0 : 1;
    }

    /**
     * Replaces the whole bag: {@code CustomColumnService.applyTo} has already merged it, and an
     * entity with a second opinion about which keys are real would be a second place to get it wrong.
     */
    public void describeCustomFields(CustomFieldValues values) {
        this.customFields = values == null ? CustomFieldValues.empty() : values;
    }

    public void moveTo(CandidateStatus newStatus) {
        this.status = newStatus;
    }

    /** Moves the person to another of the mandate's companies, or off the universe altogether. */
    public void remapTo(UUID newTriageCompanyId) {
        this.triageCompanyId = newTriageCompanyId;
    }

    /** Research resolved the employer into one of the mandate's companies — map and snapshot it. */
    public void employBy(UUID resolvedTriageCompanyId, String resolvedEmployerName) {
        this.triageCompanyId = resolvedTriageCompanyId;
        this.companyName = resolvedEmployerName;
    }

    public CandidateCompensation compensation() {
        return new CandidateCompensation(compensationCurrency, baseSalary, bonus, allowances,
                longTermIncentive, noticePeriod);
    }
}

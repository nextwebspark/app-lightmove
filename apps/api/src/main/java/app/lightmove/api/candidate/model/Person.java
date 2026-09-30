package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.text.service.LinkedInUrls;
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
import java.util.EnumSet;
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
 * One executive, owned by the workspace and shared by every mandate that maps them (V91). Everything
 * true of the human lives here — profile, background, package, contacts, research — while each
 * mandate's decision about them stays on its {@link Candidate} row.
 *
 * <p>Batched as a class so a page of candidates loads its people in one query rather than one each;
 * the talent map reads a whole mandate unpaged.
 */
@Entity
@Table(name = "app_lm_person")
@BatchSize(size = 500)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "seniority_level", length = 16)
    private Seniority seniorityLevel;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    /**
     * The profile {@link #linkedinUrl} names, as {@link LinkedInUrls#profileSlugOrNull} reads it: the key a
     * filing finds this person by, unique within the workspace (V95).
     */
    @Column(name = "profile_slug")
    private String profileSlug;

    /** The plugin read this person off that page; research and contact lookup key on its slug. */
    @Column(name = "linkedin_url_locked", nullable = false)
    private boolean linkedinUrlLocked;

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

    /** {@link BackgroundField} keys holding a value the model proposed that no researcher has changed since (V78). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_inferred_fields", nullable = false)
    private Set<String> aiInferredFields = new HashSet<>();

    /** The nationality classifier's last reading (V83) — staff-only, never on {@code CandidateResponse}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_nationality_reading")
    private NationalityReading aiNationalityReading;

    @Column(name = "summary")
    private String summary;

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
    @Column(name = "compensation_breakdown", nullable = false)
    private CompensationBreakdown compensationBreakdown = CompensationBreakdown.empty();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile", nullable = false)
    private CandidateProfile profile = CandidateProfile.empty();

    /** Which provider's research filled this in. Null until research lands, and for a person nobody researched. */
    @Enumerated(EnumType.STRING)
    @Column(name = "enriched_by", length = 16)
    private EnrichmentVendor enrichedBy;

    /** The door the person first came through; each mandate's row keeps its own. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private CandidateSource source;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /**
     * Every email and phone known for this person, whatever door or mandate it came through — V54's
     * ledger, keyed on the person since V91. A bag the aggregate rewrites from its own methods; nothing
     * outside this class adds a row. Batched at 500 because the talent map reads every mapped person in
     * one unpaged pass and every one of them is answered with its contacts.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_person_contact", joinColumns = @JoinColumn(name = "person_id"))
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

    public static Person founded(UUID workspaceId, UUID createdBy, CandidateSource source,
                                 CandidateDetails details) {
        Person person = new Person();
        person.workspaceId = workspaceId;
        person.createdBy = createdBy;
        person.source = source;
        person.describe(details, ContactSource.ofDoor(source));
        return person;
    }

    /**
     * Replaces every field the drawer edits. It submits all of them every time, and a field-by-field
     * merge would collapse "omitted" and "cleared" into one request across twenty fields.
     *
     * <p>{@code door} is which way this write came in — the drawer, a spreadsheet, the plugin — and
     * is what the ledger records against an address or number the write supplies.
     */
    public void describe(CandidateDetails details, ContactSource door) {
        this.fullName = details.fullName();
        this.title = details.title();
        confirmIfChanged(BackgroundField.SENIORITY, seniorityLevel, details.seniority());
        this.seniorityLevel = details.seniority();
        recordLinkedinUrl(details.linkedinUrl());
        this.locationCountry = details.locationCountry();
        this.locationCity = details.locationCity();
        describeBackground(details);
        this.summary = details.summary();
        describeCompensation(details.compensation());
        // Not a plain replace: the drawer resubmits only the components it renders (career,
        // languages), and a wholesale overwrite here silently wiped enrichment's fields on the first
        // edit after a capture was enriched.
        this.profile = details.profile().keepingEnrichmentOf(this.profile);
        remember(ContactChannel.EMAIL, details.emails(), door);
        remember(ContactChannel.PHONE, details.phones(), door);
    }

    /**
     * What a second mandate brings about someone already known: it fills what nobody has recorded and
     * adds to the ledger, and never overwrites a value somebody else put there. The mandate that met
     * this person first may have typed it, and a capture or a spreadsheet row is no reason to undo that.
     */
    public void fillFrom(CandidateDetails details, ContactSource door) {
        title = title == null ? details.title() : title;
        seniorityLevel = seniorityLevel == null ? details.seniority() : seniorityLevel;
        // A held URL that names no profile (a search page, a company page) is not a profile anybody
        // recorded; a filing that brings one replaces it, unless a capture already locked it.
        if (linkedinUrl == null || (!linkedinUrlLocked && LinkedInUrls.profileSlugOrNull(linkedinUrl) == null
                && LinkedInUrls.profileSlugOrNull(details.linkedinUrl()) != null)) {
            recordLinkedinUrl(details.linkedinUrl());
        }
        locationCountry = locationCountry == null ? details.locationCountry() : locationCountry;
        locationCity = locationCity == null ? details.locationCity() : locationCity;
        nationality = nationality == null ? details.nationality() : nationality;
        gender = gender == null ? details.gender() : gender;
        yearsExperience = yearsExperience == null ? details.yearsExperience() : yearsExperience;
        summary = summary == null ? details.summary() : summary;
        if (compensation().isUnknown()) {
            describeCompensation(details.compensation());
        }
        CandidateProfile supplied = details.profile();
        profile = new CandidateProfile(
                profile.career().isEmpty() ? supplied.career() : profile.career(),
                profile.languages().isEmpty() ? supplied.languages() : profile.languages(),
                profile.education(), profile.skills(), profile.enrichedAt());
        remember(ContactChannel.EMAIL, details.emails(), door);
        remember(ContactChannel.PHONE, details.phones(), door);
    }

    /**
     * The plugin read this person off {@code capturedUrl}, so the URL is the page's from here on — but
     * only when the person's URL is that page: a lock on a URL the plugin never read would keep a wrong
     * one from ever being corrected.
     */
    public void lockProfileUrl(String capturedUrl) {
        if (linkedinUrl == null) {
            return;
        }
        String held = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        boolean isThatPage = linkedinUrl.equals(capturedUrl)
                || (held != null && held.equals(LinkedInUrls.profileSlugOrNull(capturedUrl)));
        linkedinUrlLocked = linkedinUrlLocked || isThatPage;
    }

    /** Re-derived on every write, so a key V95's SQL read differently from Java heals on the next save. */
    private void recordLinkedinUrl(String url) {
        linkedinUrl = url;
        profileSlug = LinkedInUrls.profileSlugOrNull(url);
    }

    /** Another person of the workspace holds this profile; the merge tool is what folds the two. */
    public void yieldProfileKey() {
        profileSlug = null;
    }

    private void describeCompensation(CandidateCompensation compensation) {
        this.compensationCurrency = compensation.currency();
        this.baseSalary = compensation.baseSalary();
        this.bonus = compensation.bonus();
        this.allowances = compensation.allowances();
        this.longTermIncentive = compensation.longTermIncentive();
        this.noticePeriod = compensation.noticePeriod();
        this.compensationBreakdown = compensation.breakdown();
    }

    /**
     * A researcher changing one of the background fields is what confirms it; resubmitting the
     * same value leaves its AI flag standing, since nothing was actually reviewed.
     */
    private void describeBackground(CandidateDetails details) {
        confirmIfChanged(BackgroundField.NATIONALITY, nationality, details.nationality());
        confirmIfChanged(BackgroundField.GENDER, gender, details.gender());
        confirmIfChanged(BackgroundField.YEARS_EXPERIENCE, yearsExperience, details.yearsExperience());
        this.nationality = details.nationality();
        this.gender = details.gender();
        this.yearsExperience = details.yearsExperience();
    }

    /** A researcher saved the Background section: every AI-proposed value in it is now theirs. */
    public void confirmBackground() {
        aiInferredFields = new HashSet<>();
    }

    private void confirmIfChanged(BackgroundField field, Object before, Object after) {
        if (!Objects.equals(before, after) && aiInferredFields.contains(field.key())) {
            Set<String> remaining = new HashSet<>(aiInferredFields);
            remaining.remove(field.key());
            aiInferredFields = remaining;
        }
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
     * outranks a researcher. The employer is the mandate's to record ({@link Candidate#adoptEmployer}).
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
        this.enrichedBy = enriched.vendor();
        this.profile = new CandidateProfile(
                profile.career().isEmpty() ? enriched.career() : profile.career(),
                profile.languages().isEmpty() ? enriched.languages() : profile.languages(),
                enriched.education(),
                enriched.skills(),
                Instant.now().toString());
    }

    public boolean isResearched() {
        return profile.enrichedAt() != null;
    }

    /** The background fields nobody has filled in — what an inference may still propose. */
    public Set<BackgroundField> missingBackground() {
        Set<BackgroundField> missing = EnumSet.noneOf(BackgroundField.class);
        if (nationality == null) {
            missing.add(BackgroundField.NATIONALITY);
        }
        if (gender == null) {
            missing.add(BackgroundField.GENDER);
        }
        if (yearsExperience == null) {
            missing.add(BackgroundField.YEARS_EXPERIENCE);
        }
        if (seniorityLevel == null) {
            missing.add(BackgroundField.SENIORITY);
        }
        return missing;
    }

    /**
     * Fills what the model proposed into whichever fields are still empty and flags each one it filled
     * in {@link #aiInferredFields}. A value already there — typed, imported, captured or researched —
     * always stands. Answers whether anything was filled.
     */
    public boolean proposeBackground(InferredBackground proposed) {
        Set<String> inferred = new HashSet<>(aiInferredFields);
        if (gender == null && proposed.gender() != null) {
            gender = proposed.gender();
            inferred.add(BackgroundField.GENDER.key());
        }
        if (yearsExperience == null && proposed.yearsExperience() != null) {
            yearsExperience = proposed.yearsExperience();
            inferred.add(BackgroundField.YEARS_EXPERIENCE.key());
        }
        if (seniorityLevel == null && proposed.seniority() != null) {
            seniorityLevel = proposed.seniority();
            inferred.add(BackgroundField.SENIORITY.key());
        }
        boolean filled = !inferred.equals(aiInferredFields);
        aiInferredFields = inferred;
        return filled;
    }

    /**
     * Keeps the classifier's reading, replacing the last, and fills an empty nationality only from a
     * {@link NationalityReading#isDecisive decisive} one — a medium or low reading stays a suggestion the
     * researcher accepts, and Unknown leaves the field null. A value already there always stands.
     */
    public void recordNationalityReading(NationalityReading reading) {
        this.aiNationalityReading = reading;
        if (nationality == null && reading.isDecisive()) {
            nationality = reading.category();
            Set<String> inferred = new HashSet<>(aiInferredFields);
            inferred.add(BackgroundField.NATIONALITY.key());
            aiInferredFields = inferred;
        }
    }

    /**
     * Records what a contact lookup found. The ledger takes every address: the provider's own rows are
     * replaced by this answer, and an address a person had already supplied gains the provider's reading
     * (work or personal, verified) while keeping its own source. The timestamp is stamped even when
     * nothing was found. That is what makes a miss free: the channel reads as asked, and the next press
     * answers from the row instead of buying the same "nothing on record" again — on any mandate.
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

    public CandidateCompensation compensation() {
        return new CandidateCompensation(compensationCurrency, baseSalary, bonus, allowances,
                longTermIncentive, noticePeriod, compensationBreakdown);
    }
}

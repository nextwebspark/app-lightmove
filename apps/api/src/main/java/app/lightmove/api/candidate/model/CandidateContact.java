package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.ContactSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One email or phone a mandate knows for an executive, with the door it came through — a row of
 * V54's ledger, owned by {@link Candidate} the way a position owns its lists.
 *
 * <p>{@code valueKey} is the identity: the lower-cased address, or the digits of the number, because
 * providers spell one phone three ways and a researcher types an address in any case. The spelling
 * that arrived first is what {@code value} keeps.
 *
 * <p>{@code kind}, {@code verified} and {@code status} are claims, and {@code status} says whose:
 * a provider's own word ({@code "Verified"}) or {@code "Verified by researcher"} for a person's.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateContact {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 8)
    private ContactChannel channel;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "value_key", nullable = false)
    private String valueKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16)
    private ContactKind kind;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "status")
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ContactSource source;

    @Column(name = "found_at", nullable = false)
    private Instant foundAt;

    public static CandidateContact typed(ContactChannel channel, String value, ContactSource door) {
        CandidateContact contact = new CandidateContact();
        contact.channel = channel;
        contact.value = value.trim();
        contact.valueKey = keyOf(channel, value);
        contact.source = door;
        contact.foundAt = Instant.now();
        return contact;
    }

    public static CandidateContact foundEmail(CandidateEmail email, ContactSource provider, Instant at) {
        CandidateContact contact = typed(ContactChannel.EMAIL, email.address(), provider);
        contact.claim(provider, email, at);
        return contact;
    }

    public static CandidateContact foundPhone(String number, ContactSource provider, Instant at) {
        CandidateContact contact = typed(ContactChannel.PHONE, number, provider);
        contact.claim(provider, at);
        return contact;
    }

    /**
     * The identity two spellings of one contact share. Empty for a "number" with no digits in it,
     * which is the caller's cue that there is nothing to remember.
     */
    public static String keyOf(ContactChannel channel, String value) {
        String trimmed = value.trim();
        return channel == ContactChannel.PHONE
                ? trimmed.replaceAll("\\D", "")
                : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * A provider vouching for an address: its reading — work or personal, verified — is taken, and
     * the row becomes the provider's. A person may have typed it first, but "the provider holds this
     * too" is the stronger fact, and it is what lets a miss be read as "no row from the provider"
     * everywhere rather than as a rule about kinds.
     */
    public void claim(ContactSource provider, CandidateEmail email, Instant at) {
        this.kind = ContactKind.fromValue(email.kind());
        this.verified = email.isVerified();
        this.status = email.status();
        claim(provider, at);
    }

    public void claim(ContactSource provider, Instant at) {
        this.source = provider;
        this.foundAt = at;
    }

    /**
     * What an edit in the drawer does to a row it matched by key: the spelling, the kind and the
     * verified flag are taken as stated. A verified flag a person sets is recorded as theirs; one
     * a provider set stays the provider's while it is left on. Respelling a row makes it the
     * editor's, whichever door put it there — a corrected value is theirs, not the spreadsheet's or
     * the provider's.
     */
    public void describedBy(ContactEntry entry, ContactSource door, Instant at) {
        if (!value.equals(entry.value().trim())) {
            value = entry.value().trim();
            source = door;
            foundAt = at;
        }
        kind = entry.kind();
        if (entry.verified() && !verified) {
            status = VERIFIED_BY_RESEARCHER;
        }
        if (!entry.verified()) {
            status = null;
        }
        verified = entry.verified();
    }

    public static final String VERIFIED_BY_RESEARCHER = "Verified by researcher";

    public boolean is(ContactChannel wanted) {
        return channel == wanted;
    }

    public boolean isFrom(ContactSource wanted) {
        return source == wanted;
    }

    /** Typed, imported or captured — a person put it there, as opposed to a lookup. */
    public boolean isSuppliedByAPerson() {
        return source != ContactSource.CONTACTOUT;
    }

    public boolean matches(String key) {
        return valueKey.equals(key);
    }
}

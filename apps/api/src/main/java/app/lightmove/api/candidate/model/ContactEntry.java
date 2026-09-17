package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.ContactKind;

/**
 * One email or phone as a person states it — the write-side shape of a ledger row. {@code kind}
 * and {@code verified} are the person's claims; the row records who made them.
 */
public record ContactEntry(String value, ContactKind kind, boolean verified) {

    public ContactEntry {
        value = blankToNull(value);
    }

    /** A bare value, as a spreadsheet cell or the plugin's capture supplies it. */
    public static ContactEntry of(String value) {
        return new ContactEntry(value, null, false);
    }

    public boolean isBlank() {
        return value == null;
    }
}

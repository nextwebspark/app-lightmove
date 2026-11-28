package app.lightmove.api.candidate.model;

import static app.lightmove.api.core.text.service.SuppliedText.blankToNull;

import app.lightmove.api.candidate.constant.Mobility;

/**
 * What an executive expects of a move: the package that would take them, in the record's one compensation currency and
 * never converted (null is never zero), where they would go, and when they could start.
 */
public record MoveExpectations(Long expectedBaseSalary, Long expectedTotalPackage, String expectedPackageNote,
                               Mobility mobility, String mobilityNote, String availableFrom) {

    public static final int LONGEST_NOTE = 200;
    public static final int LONGEST_AVAILABLE_FROM = 60;

    public MoveExpectations {
        expectedPackageNote = blankToNull(expectedPackageNote);
        mobilityNote = blankToNull(mobilityNote);
        availableFrom = blankToNull(availableFrom);
    }

    /** What a person carries before anyone has asked what a move would take. */
    public static MoveExpectations unknown() {
        return new MoveExpectations(null, null, null, null, null, null);
    }

    public boolean isUnknown() {
        return equals(unknown());
    }

    public boolean holdsFigures() {
        return expectedBaseSalary != null || expectedTotalPackage != null;
    }

    /** Whether writing figures in {@code newCurrency} would leave these, held in {@code heldCurrency}, mislabelled. */
    public boolean wouldRelabel(String heldCurrency, String newCurrency) {
        return holdsFigures() && heldCurrency != null && !heldCurrency.equalsIgnoreCase(newCurrency);
    }
}

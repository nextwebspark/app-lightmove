package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.model.CallSuggestion;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Figure;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Notice;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Pay;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Place;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Relocation;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Step;
import app.lightmove.api.notetaker.service.CallChangesAnswer.Words;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CallChangeSuggesterTest {

    private static final UUID TAG = UUID.randomUUID();
    private static final CallHeldFields NOTHING = new CallHeldFields(null, null, null, null, null, null, null, null,
            null, TAG, false);

    @Test
    @DisplayName("a notice period folds onto one of the five only where it is an exact equivalent")
    void noticeFolds() {
        assertThat(only(answer(new Notice(90, "days", "Ninety days.", "07:42")), NOTHING).value().text())
                .isEqualTo("3 months");
        assertThat(only(answer(new Notice(0, null, "I can start tomorrow.", "07:42")), NOTHING).value().text())
                .isEqualTo("None");
        assertThat(CallChangeSuggester.suggestionsOf(answer(new Notice(6, "weeks", "Six weeks.", "07:42")), NOTHING))
                .isEmpty();
        CallHeldFields threeMonths = new CallHeldFields("3 months", null, null, null, null, null, null, null, null,
                TAG, false);
        assertThat(CallChangeSuggester.suggestionsOf(answer(new Notice(3, "MONTHS", "Three months.", "07:42")),
                threeMonths)).isEmpty();
    }

    @Test
    @DisplayName("monthly figures are annualised with the working shown, and a bonus in months is read off the base")
    void packageIsAnnualised() {
        CallSuggestion pay = only(new CallChangesAnswer(null, null, new Pay("aed", new Figure(95_000d, "MONTH"),
                null, new Figure(18_000d, "month"), 4d, "Basic is ninety-five a month.", "09:15"), null, null, null,
                null, null), NOTHING);

        assertThat(pay.kind()).isEqualTo(CallSuggestionKind.CURRENT_PACKAGE);
        assertThat(pay.value().currency()).isEqualTo("AED");
        assertThat(pay.value().baseSalary()).isEqualTo(1_140_000L);
        assertThat(pay.value().bonus()).isEqualTo(380_000L);
        assertThat(pay.value().allowances()).isEqualTo(216_000L);
        assertThat(pay.value().working()).isEqualTo(
                "Annualised from monthly: base 95,000 × 12 · bonus 4 months' base · allowances 18,000 × 12");
    }

    @Test
    @DisplayName("no currency, an unknown one, an unstated period or a held figure in another currency suggests nothing")
    void packageNeedsWhatItCanCheck() {
        assertThat(CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, new Pay(null,
                new Figure(1_000_000d, "YEAR"), null, null, null, "A million.", "09:15"), null, null, null, null,
                null), NOTHING)).isEmpty();
        assertThat(CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, new Pay("XYZ",
                new Figure(1_000_000d, "YEAR"), null, null, null, "A million.", "09:15"), null, null, null, null,
                null), NOTHING)).isEmpty();
        assertThat(CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, new Pay("AED",
                new Figure(1_000_000d, null), null, null, null, "A million.", "09:15"), null, null, null, null,
                null), NOTHING)).isEmpty();
        CallHeldFields heldInDollars = new CallHeldFields(null, "USD", 300_000L, 50_000L, null, null, null, null,
                null, TAG, false);
        assertThat(CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, new Pay("AED",
                new Figure(1_100_000d, "YEAR"), null, null, null, "One point one.", "09:15"), null, null, null, null,
                null), heldInDollars)).isEmpty();
    }

    @Test
    @DisplayName("a figure already on file in the same currency is left out of the package suggestion")
    void heldFiguresAreLeftOut() {
        CallHeldFields held = new CallHeldFields(null, "AED", 1_140_000L, null, null, null, null, null, null, TAG,
                false);
        CallSuggestion pay = only(new CallChangesAnswer(null, null, new Pay("AED", new Figure(1_140_000d, "YEAR"),
                new Figure(200_000d, "YEAR"), null, null, "Same base, bonus about two hundred.", "09:15"), null,
                null, null, null, null), held);

        assertThat(pay.value().baseSalary()).isNull();
        assertThat(pay.value().bonus()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("a location needs a country the catalog knows, and relocation needs the live tag")
    void locationAndRelocation() {
        CallSuggestion place = only(new CallChangesAnswer(null, null, null, new Place("dubai", "UAE",
                "We moved to Dubai.", "02:10"), null, null, null, null), NOTHING);
        assertThat(place.value().country()).isEqualTo("United Arab Emirates");
        assertThat(CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, null,
                new Place("Springfield", "Atlantis", "Springfield.", "02:10"), null, null, null, null), NOTHING))
                .isEmpty();

        CallChangesAnswer open = new CallChangesAnswer(null, null, null, null, new Relocation(true,
                "Riyadh would be fine.", "21:30"), null, null, null);
        assertThat(only(open, NOTHING).kind()).isEqualTo(CallSuggestionKind.OPEN_TO_RELOCATE);
        CallHeldFields noTag = new CallHeldFields(null, null, null, null, null, null, null, null, null, null, false);
        assertThat(CallChangeSuggester.suggestionsOf(open, noTag)).isEmpty();
        CallHeldFields tagged = new CallHeldFields(null, null, null, null, null, null, null, null, null, TAG, true);
        assertThat(CallChangeSuggester.suggestionsOf(open, tagged)).isEmpty();
    }

    @Test
    @DisplayName("only the consultant's own next steps become tasks, three at most, and a bad time is dropped")
    void tasksAndTimes() {
        List<Step> steps = List.of(
                new Step("Send Rania the position description", "CONSULTANT", "I'll send it tonight.", "31:02"),
                new Step("Check her bonus letter", "EXECUTIVE", "I'll check.", "10:20"),
                new Step("Call her references", "consultant", "I'll call them.", "not a time"),
                new Step("Book the second interview", "CONSULTANT", "I'll book it.", "1:02:03"),
                new Step("Share the org chart", "CONSULTANT", "I'll share it.", "33:00"));
        List<CallSuggestion> tasks = CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, null, null,
                null, null, null, steps), NOTHING);

        assertThat(tasks).extracting(task -> task.value().text()).containsExactly(
                "Send Rania the position description", "Call her references", "Book the second interview");
        assertThat(tasks).extracting(CallSuggestion::at).containsExactly("31:02", null, "1:02:03");
        assertThat(tasks).extracting(CallSuggestion::id).containsExactly("task-1", "task-2", "task-3");
    }

    @Test
    @DisplayName("motivation and an expected package are kept as words, and a blank answer suggests nothing")
    void words() {
        List<CallSuggestion> found = CallChangeSuggester.suggestionsOf(new CallChangesAnswer(null, null, null, null,
                null, new Words("Wants a group remit.", "Six years is enough.", "12:04"),
                new Words("  ", "Hm.", "27:48"), null), NOTHING);

        assertThat(found).extracting(CallSuggestion::kind).containsExactly(CallSuggestionKind.MOTIVATION);
    }

    private static CallChangesAnswer answer(Notice notice) {
        return new CallChangesAnswer(null, notice, null, null, null, null, null, null);
    }

    private static CallSuggestion only(CallChangesAnswer answer, CallHeldFields held) {
        List<CallSuggestion> found = CallChangeSuggester.suggestionsOf(answer, held);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }
}

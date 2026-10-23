package app.lightmove.api.notetaker.constant;

import app.lightmove.api.candidate.constant.CallField;
import java.util.List;

/** What a recorded call may suggest for the person's record, in the order the review walks them. */
public enum CallSuggestionKind {

    NOTICE_PERIOD(List.of(CallField.NOTICE_PERIOD)),
    /** Base, bonus and allowances in one currency; the researcher ticks which of the three to take. */
    CURRENT_PACKAGE(List.of(CallField.BASE_SALARY, CallField.BONUS, CallField.ALLOWANCES)),
    LOCATION(List.of(CallField.LOCATION)),
    OPEN_TO_RELOCATE(List.of(CallField.OPEN_TO_RELOCATE)),
    /** Added below the Assessment's Motivation section, never over it. */
    MOTIVATION(List.of(CallField.MOTIVATION)),
    EXPECTED_PACKAGE(List.of(CallField.EXPECTED_PACKAGE)),
    /** Something the consultant said they would do, filed as a task for whoever applies it. */
    TASK(List.of(CallField.TASK));

    private final List<CallField> fields;

    CallSuggestionKind(List<CallField> fields) {
        this.fields = fields;
    }

    public List<CallField> fields() {
        return fields;
    }
}

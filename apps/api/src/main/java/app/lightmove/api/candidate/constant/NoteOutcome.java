package app.lightmove.api.candidate.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** How a logged call or WhatsApp message went (V224): the sequence call step's words, each held by one kind. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NoteOutcome implements ApiValueEnum {

    CONNECTED("connected", "Connected", PersonNoteKind.CALL),
    VOICEMAIL("voicemail", "Left voicemail", PersonNoteKind.CALL),
    NO_ANSWER("no_answer", "No answer", PersonNoteKind.CALL),
    WRONG_NUMBER("wrong_number", "Wrong number", PersonNoteKind.CALL),
    SENT("sent", "Sent a message", PersonNoteKind.WHATSAPP),
    REPLIED("replied", "They replied", PersonNoteKind.WHATSAPP);

    /** A call that rang out or reached someone else never reached the person. */
    public static final Set<NoteOutcome> UNANSWERED = Collections.unmodifiableSet(EnumSet.of(NO_ANSWER, WRONG_NUMBER));

    private final String value;
    private final String label;
    private final PersonNoteKind kind;

    public boolean fits(PersonNoteKind noteKind) {
        return kind == noteKind;
    }
}

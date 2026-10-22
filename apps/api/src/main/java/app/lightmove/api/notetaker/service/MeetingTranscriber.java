package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.notetaker.constant.MeetingMediaKind;
import app.lightmove.api.notetaker.model.HeardLine;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * One model call per audio piece: the piece's speech as timed lines, each voice labelled for that piece alone.
 * Throws when the model cannot be reached or answers nothing usable, so the piece is tried again on a later pass.
 */
@Component
class MeetingTranscriber {

    private static final String PROMPT_ID = "meeting-transcribe";
    private static final String BLOCKED = "{\"lines\":[{\"offsetSeconds\":0,\"speaker\":\"" + BlockedAnswer.MARKER
            + "\",\"text\":\"\"}]}";
    /** A line longer than a minute of speech is a model running on, not a turn. */
    private static final int LONGEST_LINE = 2_000;

    private final StructuredPrompt prompt;

    MeetingTranscriber(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    List<HeardLine> transcribe(byte[] audio) {
        ModelAnswer answer = prompt.ask(ModelAnswer.class, user -> user
                .text("Transcribe this piece of the call.")
                .media(MimeTypeUtils.parseMimeType(MeetingMediaKind.AUDIO.contentType()), new ByteArrayResource(audio)));
        if (answer == null || answer.lines() == null) {
            throw new IllegalStateException("The transcriber answered nothing");
        }
        if (answer.lines().stream().anyMatch(line -> BlockedAnswer.matches(line.speaker()))) {
            throw new IllegalStateException("The transcriber's guard stopped a piece");
        }
        return answer.lines().stream()
                .filter(Objects::nonNull)
                .filter(line -> line.text() != null && !line.text().isBlank())
                .map(line -> new HeardLine(Math.max(0, Math.round(line.offsetSeconds() * 1_000)),
                        line.speaker() == null || line.speaker().isBlank() ? "Speaker" : line.speaker().strip(),
                        clip(line.text().strip())))
                .toList();
    }

    private static String clip(String text) {
        return text.length() <= LONGEST_LINE ? text : text.substring(0, LONGEST_LINE);
    }

    private record ModelAnswer(List<ModelLine> lines) {}

    private record ModelLine(double offsetSeconds, String speaker, String text) {}
}

package app.lightmove.api.assistant.constant;

import java.util.Locale;

/**
 * What one entry in a turn's log says.
 *
 * <p>An open vocabulary by design — V65 gives {@code kind} no CHECK because the kinds a turn emits
 * grow with every tool the assistant learns. The enum exists so a <i>writer</i> cannot invent a
 * misspelt one; it is deliberately not a closed set for readers.
 *
 * <p><b>There is no {@code fromWire}, and that is the point.</b> What the column stores is the wire
 * string, and the stream hands it to the browser verbatim. Parsing it back would mean an instance
 * running older code drops any kind it does not recognise — which during a rolling deploy silently
 * loses events mid-conversation. {@code ProjectStreamKind.fromWire} can afford that because every
 * project event means only "refetch"; an assistant event <i>is</i> the content.
 */
public enum AssistantEventKind {

    /** The question was accepted. Written in the same transaction as the turn row. */
    TURN_STARTED,

    /**
     * A batch of answer text, a few hundred milliseconds' worth. Emitted from the streaming runner;
     * see V65's note on why this is not a row per token.
     */
    MESSAGE_DELTA,

    /** The settled answer in full, so a replay does not have to reassemble the deltas. */
    ANSWER,

    /** The turn reached a terminal status. The stream completes after sending this. */
    TURN_FINISHED;

    /**
     * The form stored in the column and sent to the browser: {@code turn.started}. Dots rather than
     * the project stream's hyphens, because these kinds are namespaced by subject and will grow a
     * {@code tool.called} / {@code tool.result} pair.
     *
     * <p>{@code Locale.ROOT} for {@code ProjectStreamKind}'s reason: the default locale is the JVM's,
     * and a Turkish one lowercases {@code I} to {@code ı}. No name here contains one today, which is
     * exactly why the next one added would break the frontend and pass review.
     */
    public String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '.');
    }
}

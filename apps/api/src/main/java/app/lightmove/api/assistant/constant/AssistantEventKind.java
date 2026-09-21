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

    /**
     * The model asked for a tool, with the tool's name and the arguments it chose.
     *
     * <p>Written before the tool runs, so a turn that dies inside one still says what it reached
     * for. The arguments are the model's own and are recorded verbatim — that is the point of the
     * trace — so nothing reading this log may treat them as having been authorised.
     */
    TOOL_CALLED,

    /**
     * What the tool answered, including a refusal.
     *
     * <p>A refused call is a result and not an error: the turn carries on, and the panel should show
     * that the assistant asked and was told no. Why it was refused is deliberately not here — the
     * audit trail has that, and the model is told only that it may not.
     */
    TOOL_RESULT,

    /**
     * Companies the assistant is offering to file, and the stage is not among them — a proposal
     * names what, never where. It writes nothing: the rows exist only in this payload until a person
     * accepts them.
     *
     * <p>Carries the {@code projectId} the proposing tool call was <b>authorised against</b> rather
     * than the thread's, which is nullable and which the model does not have to name. The accept
     * re-authorises against this one, so it is the only mandate a proposal can ever reach.
     */
    PROPOSAL,

    /**
     * A person filed some of a proposal, with what was actually written.
     *
     * <p>Appended by the accept request rather than by a turn, and that is safe for the reason the
     * appender documents: the turn finished long ago, so it has one writer again.
     */
    PROPOSAL_ACCEPTED,

    /** The turn reached a terminal status. The stream completes after sending this. */
    TURN_FINISHED;

    /**
     * The form stored in the column and sent to the browser: {@code turn.started}. Dots rather than
     * the project stream's hyphens, because these kinds are namespaced by subject — which is what
     * {@code tool.called} and {@code tool.result} above are named for.
     *
     * <p>{@code Locale.ROOT} for {@code ProjectStreamKind}'s reason: the default locale is the JVM's,
     * and a Turkish one lowercases {@code I} to {@code ı}. No name here contains one today, which is
     * exactly why the next one added would break the frontend and pass review.
     */
    public String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '.');
    }
}

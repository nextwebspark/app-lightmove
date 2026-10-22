package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.model.HeardLine;
import java.util.List;

/**
 * Turns one audio piece into its speech: timed lines, each voice labelled for that piece alone. Throws when the
 * piece cannot be heard — the service down, or nothing usable answered — so it is tried again on a later pass.
 */
interface MeetingTranscriber {

    List<HeardLine> transcribe(byte[] audio);
}

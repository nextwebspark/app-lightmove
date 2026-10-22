import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { createPortal } from "react-dom";
import { Drawer } from "../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as notetakerApi from "../api/notetakerApi";
import { clockTimeOf, elapsedOf, meetingWhenOf } from "../lib/meetingTimes";

/**
 * A recorded call's transcript, from its row in Meetings or its Meeting note (`Position.dc.html`, `showTranscript`),
 * read by the recording, so a note whose meeting a calendar read deleted still opens it. Times count from when the
 * bot was let in, as the note's evidence does. Portalled: the executive drawer animates with a transform, which would
 * pin a fixed panel inside it.
 */
export function TranscriptSheet({
  projectId,
  candidateId,
  recordingId,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  recordingId: string;
  onClose: () => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const [query, setQuery] = useState("");
  const transcript = useQuery({
    queryKey: notetakerApi.NOTETAKER_TRANSCRIPT_KEY(projectId, candidateId, recordingId),
    queryFn: ({ signal }) => notetakerApi.getTranscript(projectId, candidateId, recordingId, signal),
  });
  const call = transcript.data;
  // Keyed by place: one voice can say two things at one offset when the transcriber splits a turn.
  const lines = (call?.lines ?? []).map((line, index) => ({ ...line, index }));
  const needle = query.trim().toLowerCase();
  const shown = needle ? lines.filter((line) => line.text.toLowerCase().includes(needle)) : lines;
  const countsFrom = call ? (call.joinedAt ?? call.startsAt) : null;
  const recordedUntil = call?.recordedUntil ?? null;

  return createPortal(
    <Drawer open onClose={onClose} wide label="Transcript">
      <header className="relative border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <PanelCloseButton onClose={onClose} />
        <div className="pe-10 text-subhead font-semibold">{call ? (call.title ?? "Untitled") : "Transcript"}</div>
        {call && <div className="mt-0.5 font-mono text-meta text-u-text3">{meetingWhenOf(call.startsAt, call.endsAt)}</div>}
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search the transcript"
          aria-label="Search the transcript"
          className="mt-3 w-full rounded-u-control border border-u-border bg-u-raised px-2.5 py-2 text-body text-u-text outline-none focus:border-u-accent"
        />
        {call && countsFrom && (
          <div className="mt-2 font-mono text-meta text-u-text3">
            {needle
              ? `${shown.length} of ${lines.length} lines`
              : `${lines.length} lines · ${recordedMinutesOf(countsFrom, recordedUntil ?? lines.at(-1)?.at ?? countsFrom)} minutes`}
          </div>
        )}
        {recordedUntil && (
          <div className="mt-2.5 rounded-u-chip border border-u-signal/40 bg-u-signal-tint px-2.5 py-[7px] text-note text-u-text2">
            Partial: the notetaker stopped at {clockTimeOf(recordedUntil)}, before the call ended. This is what it heard
            until then.
          </div>
        )}
      </header>
      <div className="min-h-0 flex-1 overflow-auto px-5 pb-4 pt-2">
        {transcript.isPending ? (
          <LinesSkeleton />
        ) : transcript.isError ? (
          <p className="mt-4 text-note text-u-text3">The transcript couldn't be loaded.</p>
        ) : lines.length === 0 ? (
          <p className="mt-4 text-note text-u-text3">Nothing was heard on this call.</p>
        ) : (
          <ol>
            {shown.map((line) => (
              <li key={line.index} className="flex gap-3 border-b border-u-border py-[9px]">
                <span className="w-[58px] flex-none font-mono text-meta/[1.9] text-u-text3">
                  {countsFrom && elapsedOf(countsFrom, line.at)}
                </span>
                <span className="min-w-0 flex-1">
                  <span
                    className={cn(
                      "text-note",
                      !line.named
                        ? "font-medium italic text-u-text3"
                        : line.speaker === call?.consultantName
                          ? "font-semibold text-u-accent"
                          : "font-semibold text-u-direct",
                    )}
                  >
                    {line.speaker}
                  </span>
                  <span className="mt-0.5 block text-body text-u-text">{line.text}</span>
                </span>
              </li>
            ))}
          </ol>
        )}
        {needle && shown.length === 0 && lines.length > 0 && (
          <p className="mt-4 text-note text-u-text3">No line matches.</p>
        )}
      </div>
      <footer className="border-t border-u-border px-5 py-2.5 font-mono text-meta text-u-text3">
        Only your team can read this. {vocabulary.contacts} never see it. Speakers are named from the call's captions; a
        voice no caption matched keeps a number.
      </footer>
    </Drawer>,
    document.body,
  );
}

/** How much of the call was recorded: from the bot being let in to where it stopped, or its last line. */
function recordedMinutesOf(from: string, until: string): number {
  return Math.max(1, Math.round((new Date(until).getTime() - new Date(from).getTime()) / 60_000));
}

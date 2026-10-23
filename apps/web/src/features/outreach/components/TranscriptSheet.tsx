import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { createPortal } from "react-dom";
import { Drawer } from "../../../components/ui/Drawer";
import { PanelCloseButton } from "../../../components/ui/PanelCloseButton";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import * as notetakerApi from "../api/notetakerApi";
import { elapsedOf, meetingDayOf } from "../lib/meetingTimes";

/**
 * A recorded call's transcript, opened from its row in Meetings. Times count from the first word said. Portalled: the
 * executive drawer animates with a transform, which would pin a fixed panel inside it.
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
  const [query, setQuery] = useState("");
  const transcript = useQuery({
    queryKey: notetakerApi.NOTETAKER_TRANSCRIPT_KEY(projectId, candidateId, recordingId),
    queryFn: ({ signal }) => notetakerApi.getTranscript(projectId, candidateId, recordingId, signal),
  });
  const call = transcript.data;
  // Keyed by place: one voice can say two things at one moment when the transcriber splits a turn.
  const lines = (call?.lines ?? []).map((line, index) => ({ ...line, index }));
  const needle = query.trim().toLowerCase();
  const shown = needle ? lines.filter((line) => line.text.toLowerCase().includes(needle)) : lines;
  const countsFrom = lines[0]?.at ?? call?.startsAt ?? null;

  return createPortal(
    <Drawer open onClose={onClose} wide label="Transcript">
      <header className="relative border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <PanelCloseButton onClose={onClose} />
        <div className="pe-10 text-subhead font-semibold">{call ? (call.title ?? "Untitled") : "Transcript"}</div>
        {call && <div className="mt-0.5 font-mono text-meta text-u-text3">{meetingDayOf(call.startsAt)}</div>}
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search the transcript"
          aria-label="Search the transcript"
          className="mt-3 w-full rounded-u-control border border-u-border bg-u-raised px-2.5 py-2 text-body text-u-text outline-none focus:border-u-accent"
        />
        {call && (
          <div className="mt-2 font-mono text-meta text-u-text3">
            {needle ? `${shown.length} of ${lines.length} lines` : `${lines.length} lines`}
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
                  <span className="text-note font-semibold text-u-text2">{line.speaker}</span>
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
        Only your team can read this. Speakers are named as they joined the call.
      </footer>
    </Drawer>,
    document.body,
  );
}

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
 * A recorded call's transcript, from its row in Meetings or its Meeting note (`Position.dc.html`, `showTranscript`).
 * Portalled: the executive drawer animates with a transform, which would pin a fixed panel inside it.
 */
export function TranscriptSheet({
  projectId,
  candidateId,
  meetingId,
  title,
  startsAt,
  endsAt,
  ownerName,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  meetingId: string;
  title: string | null;
  startsAt: string;
  endsAt: string;
  /** The consultant whose call it was: their lines are drawn apart from everyone else's. */
  ownerName: string | null;
  onClose: () => void;
}) {
  const vocabulary = useWorkspaceVocabulary();
  const [query, setQuery] = useState("");
  const transcript = useQuery({
    queryKey: notetakerApi.NOTETAKER_TRANSCRIPT_KEY(projectId, candidateId, meetingId),
    queryFn: ({ signal }) => notetakerApi.getTranscript(projectId, candidateId, meetingId, signal),
  });
  const lines = transcript.data?.lines ?? [];
  const needle = query.trim().toLowerCase();
  const shown = needle ? lines.filter((line) => line.text.toLowerCase().includes(needle)) : lines;
  const minutes = Math.max(1, Math.round((new Date(endsAt).getTime() - new Date(startsAt).getTime()) / 60_000));
  const recordedUntil = transcript.data?.recordedUntil ?? null;

  return createPortal(
    <Drawer open onClose={onClose} wide label="Transcript">
      <header className="relative border-b border-u-border px-5 pb-3.5 pt-[18px]">
        <PanelCloseButton onClose={onClose} />
        <div className="pe-10 text-subhead font-semibold">{title ?? "Untitled"}</div>
        <div className="mt-0.5 font-mono text-meta text-u-text3">{meetingWhenOf(startsAt, endsAt)}</div>
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search the transcript"
          aria-label="Search the transcript"
          className="mt-3 w-full rounded-u-control border border-u-border bg-u-raised px-2.5 py-2 text-body text-u-text outline-none focus:border-u-accent"
        />
        {transcript.isSuccess && (
          <div className="mt-2 font-mono text-meta text-u-text3">
            {needle ? `${shown.length} of ${lines.length} lines` : `${lines.length} lines · ${minutes} minutes`}
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
              <li key={`${line.at}-${line.speaker}`} className="flex gap-3 border-b border-u-border py-[9px]">
                <span className="w-[58px] flex-none font-mono text-meta/[1.9] text-u-text3">
                  {elapsedOf(startsAt, line.at)}
                </span>
                <span className="min-w-0 flex-1">
                  <span
                    className={cn(
                      "text-note",
                      !line.named
                        ? "font-medium italic text-u-text3"
                        : line.speaker === ownerName
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

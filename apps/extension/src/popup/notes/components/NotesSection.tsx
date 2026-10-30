import { useState } from "react";
import type { NoteKind, NoteTarget } from "../../../api/types";
import { FieldSection } from "../../components/FieldSection";
import { usePanelToast } from "../../lib/panelToast";
import { useNotes } from "../../hooks/useNotes";
import type { TeammateOption } from "../lib/teammates";
import { NoteComposer } from "./NoteComposer";
import { NoteCard, type NoteScope } from "./NoteParts";

/**
 * A record's notes on this position: a composer for the next one — open, since a new note is why someone comes back
 * to a person already filed — then the notes, pinned first, each pinned, edited or deleted with the toast's Undo.
 */
export function NotesSection({
  target,
  subjectName,
  teammates,
  positionTitle,
  unsent = null,
}: {
  target: NoteTarget;
  subjectName: string;
  teammates: readonly TeammateOption[];
  positionTitle: string;
  /** A note the capture could not file, handed back so its words are never lost. */
  unsent?: { body: string; kind: NoteKind; scope: NoteScope; reason: string } | null;
}) {
  const notes = useNotes(target);
  const toast = usePanelToast();
  const isPerson = target.subject === "person";
  const [kind, setKind] = useState<NoteKind>(unsent?.kind ?? "general");
  const [scope, setScope] = useState<NoteScope>(unsent?.scope ?? "position");
  const [draft, setDraft] = useState(unsent?.body ?? "");
  const [unsentReason, setUnsentReason] = useState(unsent?.reason ?? null);

  const save = () => {
    const body = draft.trim();
    if (!body || notes.write.isPending) return;
    notes.write.mutate(
      { kind, body, format: "markdown", ...(isPerson && scope === "everywhere" ? { allPositions: true } : {}) },
      {
        onSuccess: () => {
          setDraft("");
          setUnsentReason(null);
          toast(isPerson && scope === "everywhere" ? "Note saved — your team sees it on every position" : "Note saved");
        },
      },
    );
  };

  return (
    <FieldSection label={`Notes · ${notes.notes.length}`} hint="Your team only">
      {unsentReason && (
        <p role="alert" className="mb-2 rounded-u-control bg-u-offlimits-tint px-2.5 py-1.5 text-note text-u-offlimits">
          Your note was not saved: {unsentReason} It is below — save it again.
        </p>
      )}
      <NoteComposer
        kind={kind}
        onKindChange={setKind}
        draft={draft}
        onDraftChange={setDraft}
        scope={isPerson ? scope : undefined}
        onScopeChange={isPerson ? setScope : undefined}
        teammates={isPerson ? teammates : null}
        positionTitle={positionTitle}
        expandTitle={`Note on ${subjectName}`}
        onSubmit={save}
        save={{ label: "Save note", isSaving: notes.write.isPending, onSave: save }}
      />
      {notes.write.error && (
        <p role="alert" className="mt-2 text-note text-u-offlimits">
          {notes.write.error.message}
        </p>
      )}
      {notes.isLoading ? (
        <div aria-busy="true" className="mt-2.5 h-16 animate-pulse rounded-[8px] bg-u-raised" />
      ) : notes.loadError ? (
        <p className="mt-2.5 text-note text-u-text3">{notes.loadError.message}</p>
      ) : notes.notes.length === 0 ? (
        <p className="mt-2.5 text-note text-u-text3">
          No notes yet. What your team learns about {subjectName} for this position sits here.
        </p>
      ) : (
        <ul className="mt-2.5 flex flex-col gap-2">
          {notes.notes.map((note) => (
            <NoteCard
              key={note.id}
              note={note}
              teammates={isPerson && note.projectId ? teammates : null}
              actions={{
                busy: notes.isBusy,
                onPin: () => notes.pin.mutate({ noteId: note.id, pinned: !note.pinned }),
                onRevise: (body) =>
                  notes.revise.mutate({ noteId: note.id, note: { kind: note.kind, body, format: "markdown" } }),
                onRemove: () =>
                  notes.remove.mutate(note.id, {
                    onSuccess: () => toast("Note deleted", () => notes.restore.mutate(note.id)),
                  }),
              }}
            />
          ))}
        </ul>
      )}
    </FieldSection>
  );
}

import { useMutation, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Select, TextArea, useToast } from "../../../../components/ui";
import { messageFor } from "../../../../lib/errorCodes";
import { useSubmitShortcut } from "../../../../lib/useSubmitShortcut";
import * as poolApi from "../../api/poolApi";
import type { PersonNote, PersonNoteKind, PersonRecord } from "../../api/types";
import { NOTE_KINDS } from "../../lib/noteKinds";
import { NoteCard, NoteKindPicker } from "../NoteParts";

/**
 * The Notes tab: a composer about a position or about the person, and the team's notes, pinned first.
 * Pinning is anyone's; changing or deleting a note is its author's or an admin's.
 */
export function PersonNotesTab({
  person,
  notes,
}: {
  person: PersonRecord;
  notes: UseQueryResult<PersonNote[]>;
}) {
  const toast = useToast();
  const refresh = useNoteRefresh(person.personId);
  const [kind, setKind] = useState<PersonNoteKind>("call");
  const [about, setAbout] = useState("");
  const [text, setText] = useState("");

  const saving = useMutation({
    mutationFn: () =>
      poolApi.writePoolNote(person.personId, { kind, body: text.trim(), projectId: about || null }),
    onSuccess: () => {
      setText("");
      toast("Note saved — your team sees it on every position");
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const canSave = text.trim().length > 0 && !saving.isPending;
  const handleKeyDown = useSubmitShortcut(() => canSave && saving.mutate());
  const list = notes.data ?? [];

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-[8px] border border-u-border p-2.5">
        <NoteKindPicker value={kind} onChange={setKind} />
        <TextArea
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={3}
          maxLength={4000}
          aria-label="New note"
          placeholder={NOTE_KINDS.find((option) => option.value === kind)?.placeholder}
          className="font-sans text-[13px]/[1.55]"
        />
        <div className="mt-2 flex items-center gap-2">
          <Select
            aria-label="Position this note is about"
            value={about}
            onChange={(event) => setAbout(event.target.value)}
            className="w-auto min-w-0 flex-1 py-1 text-[12.5px]"
          >
            <option value="">No position — about the person</option>
            {person.positions.map((position) => (
              <option key={position.projectId} value={position.projectId}>
                {position.positionTitle ?? "A position"}
              </option>
            ))}
          </Select>
          <Button className="px-3 py-1.5 text-[12.5px]" disabled={!canSave} loading={saving.isPending} onClick={() => saving.mutate()}>
            Save note
          </Button>
        </div>
      </div>
      <p className="text-[12px] text-u-text3">
        Shared with your whole team, on every position this person is in. Client contacts never see notes.
      </p>

      {notes.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(notes.error)}</p>
      ) : notes.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : list.length === 0 ? (
        <p className="text-[13px]/[1.6] text-u-text3">
          No notes yet. The first call or meeting you log will sit here for the whole team.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {list.map((note) => (
            <PoolNoteCard key={note.id} personId={person.personId} note={note} />
          ))}
        </ul>
      )}
    </div>
  );
}

function PoolNoteCard({ personId, note }: { personId: string; note: PersonNote }) {
  const toast = useToast();
  const refresh = useNoteRefresh(personId);
  const pinning = useMutation({
    mutationFn: () => poolApi.pinPoolNote(personId, note.id, !note.pinned),
    onSuccess: refresh,
    onError: (error) => toast.error(messageFor(error)),
  });
  const revising = useMutation({
    mutationFn: (body: string) =>
      poolApi.revisePoolNote(personId, note.id, { kind: note.kind, body, projectId: note.projectId }),
    onSuccess: refresh,
    onError: (error) => toast.error(messageFor(error)),
  });
  const removing = useMutation({
    mutationFn: () => poolApi.removePoolNote(personId, note.id),
    onSuccess: () => {
      toast("Note deleted — the timeline keeps a line saying so");
      refresh();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <NoteCard
      note={note}
      actions={{
        onPin: () => pinning.mutate(),
        onRevise: (body) => revising.mutate(body),
        onRemove: () => removing.mutate(),
        busy: pinning.isPending || revising.isPending || removing.isPending,
      }}
    />
  );
}

/** A note changes the notes, the timeline and the feed; nothing else about the person. */
function useNoteRefresh(personId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: poolApi.POOL_NOTES_KEY(personId) });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "timeline", personId] });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "activity"] });
    void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "page"] });
  };
}
